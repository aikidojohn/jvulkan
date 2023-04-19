package com.johnhite.game.vulkan

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

fun checkVk(errcode: Int) {
    if (errcode != 0) {
        throw IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
    }
}

class Game {

    private val validate: Boolean = true
    private val maxFramesInFlight = 2

    private var window: Long = -1L
    private var surface: Long = -1L
    private lateinit var instance: VkInstance
    private var debugMessenger: DebugMessenger? = null
    private lateinit var devices: List<PhysicalDevice>
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

    private val extensionNames = MemoryUtil.memAllocPointer(64)
    private val debugUtilExtensionName = MemoryUtil.memASCII(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
    private val portabilityExtensionName = MemoryUtil.memASCII(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)

    /*private fun checkVk(errcode: Int) {
        if (errcode != 0) {
            throw IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
        }
    }*/

    private fun checkLayers(stack: MemoryStack, available: VkLayerProperties.Buffer, vararg layers: String) : PointerBuffer? {
        val required = stack.mallocPointer(layers.size)
        for (i in layers.indices) {
            var found = false
            for (j in 0 until available.capacity()) {
                available.position(j)
                if (layers[i] == available.layerNameString()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.format("Cannot find layer: %s\n", layers[i]);
                return null;
            }

            required.put(i, stack.ASCII(layers[i]));
        }
        return required
    }

    private fun getRequiredExtensions(stack: MemoryStack) : PointerBuffer {
        val requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
            ?: throw IllegalStateException("glfwGetRequiredInstanceExtensions failed to find the platform surface extensions.")

        for (i in 0 until requiredExtensions.capacity()) {
            extensionNames.put(requiredExtensions[i])
        }
        //MoltenVK requires the VK_HKR_PORTABILITY_subset extension
        if (OS.isOSX) {
            extensionNames.put(portabilityExtensionName)
        }
        //Add debug extensions if validation layers enabled
        if (validate) {
            val ip = stack.mallocInt(1)
            checkVk(VK10.vkEnumerateInstanceExtensionProperties(null as String?, ip, null))
            if (ip[0] != 0) {
                val instanceExtensions = VkExtensionProperties.malloc(ip[0], stack)
                checkVk(VK10.vkEnumerateInstanceExtensionProperties(null as String?, ip, instanceExtensions))

                for (i in 0 until ip[0]) {
                    instanceExtensions.position(i)
                    if (EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME == instanceExtensions.extensionNameString()) {
                        extensionNames.put(debugUtilExtensionName)
                    }
                }
            }
        }
        extensionNames.flip()
        return extensionNames
    }

    private fun getRequiredValidationLayers(stack: MemoryStack) : PointerBuffer {
        var requiredLayers: PointerBuffer? = null
        val ip = stack.mallocInt(1)
        checkVk(vkEnumerateInstanceLayerProperties(ip, null))
        if (ip[0] > 0) {
            val availableLayers = VkLayerProperties.malloc(ip[0], stack)
            checkVk(vkEnumerateInstanceLayerProperties(ip, availableLayers))

            // VulkanSDK 1.1.106+
            requiredLayers = checkLayers(
                stack, availableLayers,
                "VK_LAYER_KHRONOS_validation" /*,
                        "VK_LAYER_LUNARG_assistant_layer"*/
            )
            if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                requiredLayers = checkLayers(
                    stack, availableLayers,
                    "VK_LAYER_LUNARG_standard_validation" /*,
                            "VK_LAYER_LUNARG_assistant_layer"*/
                )
            }
            if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                requiredLayers = checkLayers(
                    stack, availableLayers,
                    "VK_LAYER_GOOGLE_threading",
                    "VK_LAYER_LUNARG_parameter_validation",
                    "VK_LAYER_LUNARG_object_tracker",
                    "VK_LAYER_LUNARG_core_validation",
                    "VK_LAYER_GOOGLE_unique_objects" /*,
                            "VK_LAYER_LUNARG_assistant_layer"*/
                )
            }
        }
        checkNotNull(requiredLayers) { "vkEnumerateInstanceLayerProperties failed to find required validation layer." }
        return requiredLayers
    }

    private fun getPhysicalDevices() : List<PhysicalDevice> {
        val deviceList = ArrayList<PhysicalDevice>()
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            checkVk(vkEnumeratePhysicalDevices(instance, ip, null))
            if (ip[0] > 0) {
                val devices = stack.mallocPointer(ip[0])
                checkVk(vkEnumeratePhysicalDevices(instance, ip, devices))
                for (i in 0 until ip[0]) {
                    deviceList.add(PhysicalDevice(VkPhysicalDevice(devices[i], instance)))
                }
            } else {
                throw IllegalStateException("vkEnumeratePhysicalDevices reported zero accessible devices.")
            }
        }
        return deviceList
    }

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


    private fun initVkInstance() : VkInstance {
        MemoryStack.stackPush().use { stack ->

            var requiredLayers: PointerBuffer? = getRequiredValidationLayers(stack)
            val requiredExtensions = getRequiredExtensions(stack)

            val version = VK.getInstanceVersionSupported()
            println("Supported Vulkan Version: ${VK_API_VERSION_MAJOR(version)}.${VK_API_VERSION_MINOR(version)}.${VK_API_VERSION_PATCH(version)}.${VK_API_VERSION_VARIANT(version)}")
            val appName = stack.UTF8("tri")
            val app = VkApplicationInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pApplicationName(appName)
                .applicationVersion(0)
                .pEngineName(appName)
                .engineVersion(0)
                .apiVersion(VK.getInstanceVersionSupported())

            val flags = if (OS.isOSX) VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR else 0
            val instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(flags)
                .pApplicationInfo(app)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            if (validate) {
                instanceCreateInfo.pNext(DebugMessenger.getDebugUtilsCreateInfo().address())
            }
            val pp = stack.mallocPointer(1)
            var err = vkCreateInstance(instanceCreateInfo, null, pp)
            check(err != VK10.VK_ERROR_INCOMPATIBLE_DRIVER) { "Cannot find a compatible Vulkan installable client driver (ICD)." }
            check(err != VK10.VK_ERROR_EXTENSION_NOT_PRESENT) { "Cannot find a specified extension library. Make sure your layers path is set appropriately." }
            check(err == 0) { "vkCreateInstance failed. Do you have a compatible Vulkan installable client driver (ICD) installed?" }
            this.instance = VkInstance(pp[0], instanceCreateInfo)

            MemoryUtil.memFree(debugUtilExtensionName)
            return this.instance
        }
    }

    private fun initDebugMessenger() {
        debugMessenger = DebugMessenger(instance)
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

            vkCmdDraw(commandBuffer, 3, 1,0, 0)
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
        MemoryStack.stackPush().use { stack ->
            val stagingBuffer = Buffers.createBuffer(device, 15*4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT )
            val buffer = Buffers.createBuffer(device, 15 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_SHARING_MODE_EXCLUSIVE, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
            val vertexBuffer = SimpleVertexBuffer(buffer)
            stagingBuffer.mapFloatBuffer()
                .put(floatArrayOf(
                    0f, -.5f,   1f, 0f, 0f,
                    .5f, .5f,   0f, 1f, 0f,
                    -.5f, .5f,  0f, 0f, 1f,
                )).rewind()
            stagingBuffer.unmap()
            Buffers.copy(stagingBuffer, buffer, device, commandPool)
            stagingBuffer.close()
            return vertexBuffer
        }
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
            initVkInstance()
            initSurface()
            initDebugMessenger()
            devices = getPhysicalDevices()
            for (device in devices) {
                println(device)
            }
            gpu = selectPhysicalDevice(devices)
            device = LogicalDevice(gpu)
            createCommandPool()
            swapchain = SwapChain(device, surface, window)
            renderPass = RenderPass(device, swapchain)

            vertexBuffer = loadVertexData()
            pipeline = Pipeline(device, renderPass, vertexBuffer)
            for (i in swapchain.imageViews.indices) {
                frameBuffers.add(FrameBuffer(device, renderPass, swapchain, i))
            }
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
        vertexBuffer.close()
        if (commandPool != 0L) {
            vkDestroyCommandPool(device.device, commandPool, null)
        }

        for (i in 0 until maxFramesInFlight) {
            vkDestroyFence(device.device, inFlightFence[i], null)
            vkDestroySemaphore(device.device, imageAvailableSemaphore[i], null)
            vkDestroySemaphore(device.device, renderFinishedSemaphore[i], null)
        }

        if (this::pipeline.isInitialized) {
            pipeline.close()
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
        if (this::devices.isInitialized) {
            for (d in devices) {
                d.close()
            }
        }

        vkDestroySurfaceKHR(instance, surface, null)
        if (validate) {
            debugMessenger?.destroy()
        }
        if (this::instance.isInitialized) {
            vkDestroyInstance(instance, null)
        }
        glfwDestroyWindow(window)
        glfwTerminate()
    }
}


fun main(args: Array<String>) {
    val game = Game()
    game.run()
}