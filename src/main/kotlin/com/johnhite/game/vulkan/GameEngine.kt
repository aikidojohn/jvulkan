package com.johnhite.game.vulkan

import com.charleskorn.kaml.Yaml
import com.johnhite.game.vulkan.shader.Shader
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.FloatBuffer

fun checkVk(errcode: Int) {
    if (errcode != 0) {
        throw IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
    }
}

class Game (private val config: VulkanContextConfiguration) {
    private val maxFramesInFlight = 2

    private var window: Long = -1L
    private var surface: Long = -1L

    lateinit var context: VulkanContext
    private val instance: VkInstance
        get() = context.instance
    private val devices: List<PhysicalDevice>
        get() = context.physicalDevices

    private lateinit var gpu: PhysicalDevice
    private lateinit var device: LogicalDevice
    private var commandPool: Long = 0L
    private var commandBuffers = ArrayList<VkCommandBuffer>()
    private lateinit var swapchain: SwapChain
    private lateinit var pipeline: Pipeline
    private lateinit var renderPass: RenderPass
    private val frameBuffers = ArrayList<FrameBuffer>()
    private var imageAvailableSemaphore = LongArray(maxFramesInFlight)
    private var renderFinishedSemaphore = LongArray(maxFramesInFlight)
    private var inFlightFence = LongArray(maxFramesInFlight)
    private var currentFrame = 0

    private lateinit var vertexBuffer: SimpleVertexBuffer
    private lateinit var indexBuffer: VulkanBuffer
    private val uniformBuffers = ArrayList<VulkanBuffer>()
    private val uniformBuffersMapped = ArrayList<FloatBuffer>()
    private var descriptorPool: Long = 0L
    private val descriptorSets = ArrayList<Long>()
    private lateinit var shaders: List<Shader>

    private fun selectPhysicalDevice(devices: List<PhysicalDevice>) : PhysicalDevice {
        var highestRank = -1
        var bestDevice = devices[0]
        val requiredExtensions = setOf<String>(VK_KHR_SWAPCHAIN_EXTENSION_NAME)

        for (device in devices) {
            var rank = 0
            device.checkSurfaceSupport(surface)
            if (device.queueSurfaceSupportIndices.isNotEmpty()) {
                rank += 1000
            }
            //check for required extensions
            if (device.supportsExtensions(requiredExtensions)) {
                rank += 1000
            }

            //Check device type
            if (device.properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                rank += 1000
            }

            device.filterQueueFamilies { q ->
                if ((q.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0) {
                    rank += 100
                }
                if ((q.queueFlags() and VK_QUEUE_COMPUTE_BIT) != 0) {
                    rank += 10
                }
                if ((q.queueFlags() and VK_QUEUE_TRANSFER_BIT) != 0) {
                    rank += 10
                }
                false
            }
            if (rank > highestRank) {
                highestRank = rank
                bestDevice = device
            }
        }
        return bestDevice
    }

    private fun initWindow(width: Int, height: Int, title: String) {
        GLFWErrorCallback.createPrint().set();
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        if (!glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver")
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        window = glfwCreateWindow(width, height, title, 0, 0)
        glfwSetFramebufferSizeCallback(window) { win: Long, newWidth: Int, newHeight: Int ->
            recreateSwapChain()
        }
    }

    private fun initSurface() {
        MemoryStack.stackPush().use {stack ->
            val lp = stack.mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(instance, window, null, lp)
            surface = lp[0]
        }
    }

    private fun createCommandPool() {
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            val poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .`sType$Default`()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(device.graphicsQueueIndex)

            checkVk(vkCreateCommandPool(device.device, poolInfo, null, lp))
            commandPool = lp[0]

            val commandInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .`sType$Default`()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(maxFramesInFlight)

            val pp = stack.mallocPointer(maxFramesInFlight)
            checkVk(vkAllocateCommandBuffers(device.device, commandInfo, pp))
            commandBuffers.add(VkCommandBuffer(pp[0], device.device))
            commandBuffers.add(VkCommandBuffer(pp[1], device.device))
        }
    }

    fun recordCommandBuffer(imageIndex: Int, commandBuffer: VkCommandBuffer) {
        MemoryStack.stackPush().use { stack->
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .`sType$Default`()
            checkVk(vkBeginCommandBuffer(commandBuffer, beginInfo))


            val clearValue = VkClearValue.calloc(1, stack)
            clearValue[0].color()
                .float32(0, 0f)
                .float32(1, 0f)
                .float32(2, 0f)
                .float32(3, 1f)

            val renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(renderPass.renderPass)
                .framebuffer(frameBuffers[imageIndex].frameBuffer)
                .renderArea { renderArea->
                    val offset = VkOffset2D.malloc(stack)
                    offset.set(0, 0)
                    renderArea.offset(offset)
                        .extent(swapchain.extent)
                }
                .clearValueCount(1)
                .pClearValues(clearValue)

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.graphicsPipeline)

            vkCmdBindVertexBuffers(commandBuffer, 0, longArrayOf(vertexBuffer.buffer.bufferPtr), longArrayOf(0L))
            vkCmdBindIndexBuffer(commandBuffer, indexBuffer.bufferPtr, 0, VK_INDEX_TYPE_UINT32)

            val viewport = VkViewport.calloc(1, stack)
            viewport[0]
                .x(0f)
                .y(0f)
                .width(swapchain.extent.width().toFloat())
                .height(swapchain.extent.height().toFloat())
                .minDepth(0f)
                .maxDepth(1f)
            vkCmdSetViewport(commandBuffer, 0,  viewport)

            val scissor = VkRect2D.calloc(1, stack)
            scissor[0].offset()
                .x(0)
                .y(0)
            scissor[0].extent(swapchain.extent)
            vkCmdSetScissor(commandBuffer, 0, scissor)

            //vkCmdDraw(commandBuffer, 3, 1,0, 0)
            val ds = stack.mallocLong(1)
            ds.put(descriptorSets[imageIndex]).rewind()
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipelineLayout, 0, ds, null)
            vkCmdDrawIndexed(commandBuffer, (indexBuffer.size / 4).toInt(), 1, 0, 0,0)
            vkCmdEndRenderPass(commandBuffer)

            checkVk(vkEndCommandBuffer(commandBuffer))
        }
    }

    fun createSyncObjects() {
        MemoryStack.stackPush().use { stack ->
            val semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .`sType$Default`()

            val fenceInfo = VkFenceCreateInfo.calloc(stack)
                .`sType$Default`()
                .flags(VK_FENCE_CREATE_SIGNALED_BIT)

            val lp = stack.mallocLong(1)
            for (i in 0 until maxFramesInFlight) {
                vkCreateSemaphore(device.device, semaphoreInfo, null, lp)
                imageAvailableSemaphore[i] = lp[0]
                vkCreateSemaphore(device.device, semaphoreInfo, null, lp)
                renderFinishedSemaphore[i] = lp[0]
                vkCreateFence(device.device, fenceInfo, null, lp)
                inFlightFence[i] = lp[0]
            }
        }
    }

    fun recreateSwapChain() {
        vkDeviceWaitIdle(device.device)

        for (f in frameBuffers) {
            f.close()
        }
        frameBuffers.clear()
        swapchain.close()

        swapchain = SwapChain(device, surface, window)
        for (i in swapchain.imageViews.indices) {
            frameBuffers.add(FrameBuffer(device, renderPass, swapchain, i))
        }
    }

    fun loadVertexData() : SimpleVertexBuffer {
        val stagingBuffer = Buffers.createBuffer(device, 20*4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT )
        val buffer = Buffers.createBuffer(device, 20 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
        stagingBuffer.mapFloatBuffer()
            .put(floatArrayOf(
                -.5f, -.5f,   1f, 0f, 0f,
                .5f, -.5f,   0f, 1f, 0f,
                 .5f, .5f,  0f, 0f, 1f,
                -.5f, .5f,  1f, 1f, 1f,
            )).rewind()
        stagingBuffer.unmap()
        Buffers.copy(stagingBuffer, buffer, device, commandPool)
        stagingBuffer.close()
        return SimpleVertexBuffer(buffer)
    }

    fun loadIndexData() : VulkanBuffer {
        val stagingBuffer = Buffers.createBuffer(device, 6 * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT )
        val buffer = Buffers.createBuffer(device, 6 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
        stagingBuffer.mapIntBuffer()
            .put(intArrayOf(0,1,2,2,3,0)).rewind()
        stagingBuffer.unmap()
        Buffers.copy(stagingBuffer, buffer, device, commandPool)
        stagingBuffer.close()
        return buffer
    }

    fun createDescriptorPool() { //TODO move to shader?
        MemoryStack.stackPush().use { stack ->
            val poolSize = VkDescriptorPoolSize.calloc(1, stack)
            poolSize[0].type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(maxFramesInFlight)

            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .`sType$Default`()
                .pPoolSizes(poolSize)
                .maxSets(maxFramesInFlight)

            val lp = stack.mallocLong(1)
            checkVk(vkCreateDescriptorPool(device.device, poolInfo, null, lp))
            descriptorPool = lp[0]

            //create descriptor sets for UBOs
            val layouts = stack.mallocLong(maxFramesInFlight)
            layouts.put(pipeline.bufferLayouts[0])
            layouts.put(pipeline.bufferLayouts[0])
            layouts.rewind()
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .`sType$Default`()
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts)

            val lp2 = stack.mallocLong(maxFramesInFlight)
            checkVk(vkAllocateDescriptorSets(device.device, allocInfo, lp2))

            for (i in 0 until maxFramesInFlight) {
                descriptorSets.add(lp2[i])
                val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                bufferInfo[0].buffer(uniformBuffers[i].bufferPtr)
                    .offset(0)
                    .range(uniformBuffers[i].size)

                val descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                descriptorWrite[0].`sType$Default`()
                    .dstSet(descriptorSets[i])
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo)

                vkUpdateDescriptorSets(device.device, descriptorWrite, null)
            }
        }
    }

    private var startTime: Long = System.nanoTime()
    fun updateUniformBuffers() {
        val now = System.nanoTime()
        val seconds = (now - startTime).toDouble() / 1000000000.0
        var model = Matrix4f().rotate(AxisAngle4f(Math.toRadians(90.0f) * seconds.toFloat(), 0f, 0f, 1f))
        var view = Matrix4f().lookAt(Vector3f(2f, 2f,2f), Vector3f(0f,0f,0f), Vector3f(0f,0f,1f))
        var projection = Matrix4f().perspective(Math.toRadians(45f), swapchain.extent.width().toFloat() / swapchain.extent.height().toFloat(), 0.1f, 10f)
        projection.m11(projection.m11() * -1)

        val ubo = uniformBuffersMapped[currentFrame]
        var pos = 0
        model.get(ubo)
        pos += 16
        ubo.position(pos)
        view.get(ubo)
        pos += 16
        ubo.position(pos)
        projection.get(ubo)
        pos += 16
        ubo.position(pos)
        ubo.rewind()
        /*println("model")
        println(model.toString())
        println("UBO")
        for (i in 0 until ubo.capacity()) {
            print("${ubo[i]},")
        }
        println();*/
    }

    fun drawFrame() {
        MemoryStack.stackPush().use { stack->
            vkWaitForFences(device.device, inFlightFence[currentFrame], true, ULong.MAX_VALUE.toLong())

            val ip = stack.mallocInt(1)
            val result = vkAcquireNextImageKHR(device.device, swapchain.swapchain, ULong.MAX_VALUE.toLong(), imageAvailableSemaphore[currentFrame], VK_NULL_HANDLE, ip)
            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapChain()
                return
            } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw RuntimeException("Failed to acquire next image")
            }
            val imageIndex = ip[0]

            vkResetFences(device.device, inFlightFence[currentFrame])

            vkResetCommandBuffer(commandBuffers[currentFrame], 0)
            recordCommandBuffer(imageIndex, commandBuffers[currentFrame])

            val waitSemaphore = stack.mallocLong(1)
            waitSemaphore.put(imageAvailableSemaphore[currentFrame])
            waitSemaphore.rewind()

            val signalSemaphore = stack.mallocLong(1)
            signalSemaphore.put(renderFinishedSemaphore[currentFrame])
            signalSemaphore.rewind()

            val waitStages = stack.mallocInt(1)
            waitStages.put(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            waitStages.rewind()

            val commandBufferPointer = stack.mallocPointer(1)
            commandBufferPointer.put(commandBuffers[currentFrame].address())
            commandBufferPointer.rewind()

            val submitInfo = VkSubmitInfo.calloc(stack)
                .`sType$Default`()
                .pWaitSemaphores(waitSemaphore)
                .waitSemaphoreCount(1)
                .pWaitDstStageMask(waitStages)
                .pCommandBuffers(commandBufferPointer)
                .pSignalSemaphores(signalSemaphore)

            updateUniformBuffers()
            checkVk(vkQueueSubmit(device.graphicsQueue, submitInfo, inFlightFence[currentFrame]))

            val swapChains = stack.mallocLong(1)
            swapChains.put(swapchain.swapchain)
            swapChains.rewind()

            ip.rewind()
            ip.put(imageIndex)
            ip.rewind()
            val presentInfo = VkPresentInfoKHR.calloc(stack)
                .`sType$Default`()
                .pWaitSemaphores(signalSemaphore)
                .swapchainCount(1)
                .pSwapchains(swapChains)
                .pImageIndices(ip)

            val presentResult = vkQueuePresentKHR(device.graphicsQueue, presentInfo)
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
                recreateSwapChain()
            } else if (presentResult != VK_SUCCESS) {
                throw RuntimeException("Failed to present image")
            }

            currentFrame= (currentFrame+1) % maxFramesInFlight
        }
    }
    fun run() {
        try {
            initWindow(1024, 768, "Vulkan")
            context = VulkanContext(config)
            initSurface()
            for (device in devices) {
                println(device)
            }
            gpu = selectPhysicalDevice(devices)
            device = LogicalDevice(gpu)
            createCommandPool()
            swapchain = SwapChain(device, surface, window)
            renderPass = RenderPass(device, swapchain)

            vertexBuffer = loadVertexData()
            indexBuffer = loadIndexData()
            shaders = listOf(
                Shader.load(device, "shaders/default.vert"),
                Shader.load(device, "shaders/default.frag")
            )
            pipeline = GraphicsPipelineBuilder().use { builder ->
                val uboLayoutBinding = VkDescriptorSetLayoutBinding.calloc(1, MemoryStack.stackGet())
                uboLayoutBinding[0].binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)

                builder.withRenderPass(renderPass)
                    .withShaderStages(shaders)
                    .withLayoutBindings(uboLayoutBinding)
                    .vertexInput(vertexBuffer)
                    .build(device)
            }

            for (i in swapchain.imageViews.indices) {
                frameBuffers.add(FrameBuffer(device, renderPass, swapchain, i))
                //TODO move to shader?
                val ubo = Buffers.createBuffer(device, 16 * 3 * 4, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT )
                uniformBuffers.add(ubo)
                uniformBuffersMapped.add(ubo.mapFloatBuffer())
            }
            createDescriptorPool() //TODO is this Uniform specific? Move to Shader?
            createSyncObjects()

            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents()
                drawFrame()
            }
            vkDeviceWaitIdle(device.device)
        }
        finally {
            destroy()
        }
    }

    private fun destroy() {
        if (this::indexBuffer.isInitialized) {
            indexBuffer.close()
        }
        if (this::vertexBuffer.isInitialized) {
            vertexBuffer.close()
        }
        if (commandPool != 0L) {
            vkDestroyCommandPool(device.device, commandPool, null)
        }

        for (i in 0 until maxFramesInFlight) {
            vkDestroyFence(device.device, inFlightFence[i], null)
            vkDestroySemaphore(device.device, imageAvailableSemaphore[i], null)
            vkDestroySemaphore(device.device, renderFinishedSemaphore[i], null)
        }

        if (descriptorPool != 0L) {
            vkDestroyDescriptorPool(device.device, descriptorPool, null)
        }

        if (this::pipeline.isInitialized) {
            pipeline.close()
        }
        if (this::shaders.isInitialized) {
            for (shader in shaders) {
                shader.close()
            }
        }
        for (ubo in uniformBuffers) {
            ubo.close()
        }
        for (f in frameBuffers) {
            f.close()
        }
        if (this::swapchain.isInitialized) {
            swapchain.close()
        }
        if (this::renderPass.isInitialized) {
            renderPass.close()
        }
        if (this::device.isInitialized) {
            device.close()
        }

        vkDestroySurfaceKHR(instance, surface, null)

        if (this::context.isInitialized) {
            context.close()
        }
        glfwDestroyWindow(window)
        glfwTerminate()
    }
}


fun main(args: Array<String>) {
    val config = Yaml.default.decodeFromString(VulkanContextConfiguration.serializer(), Resources.read("config/engine-dev.yaml"))
    val game = Game(config)
    game.run()
}