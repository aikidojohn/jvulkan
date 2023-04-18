package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*

class LogicalDevice(val physical: PhysicalDevice) : AutoCloseable {
    val graphicsQueue: VkQueue
    val presentQueue: VkQueue
    val device: VkDevice
    val presentEqualsGraphics: Boolean
    val queueFamilyIndices: Set<Int>
    val presentQueueIndex: Int
    val graphicsQueueIndex: Int

    init {
        //TODO queues should be a constructor parameter
        val graphicsQueues = physical.filterQueueFamilies { q ->
            (q.queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0
        }

        presentQueueIndex = physical.queueSurfaceSupportIndices[0]
        graphicsQueueIndex = graphicsQueues[0]
        val queues = setOf(presentQueueIndex, graphicsQueueIndex)
        presentEqualsGraphics = queues.size == 1
        queueFamilyIndices = queues

        MemoryStack.stackPush().use { stack ->
            val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(queues.size, stack)
            val priority = stack.mallocFloat(1)
            priority.put(1.0f)
            priority.rewind()

            for ((i, qIndex) in queues.withIndex()) {
                queueCreateInfo[i].sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                queueCreateInfo[i].queueFamilyIndex(qIndex)
                queueCreateInfo[i].pQueuePriorities(priority)
                queueCreateInfo[i].pNext(MemoryUtil.NULL)
                queueCreateInfo[i].flags(0)
            }

            //TODO extension list should be a constructor parameter
            val enabledExtensions = stack.mallocPointer(1)
            enabledExtensions.put(stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            enabledExtensions.rewind()

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
            deviceCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            deviceCreateInfo.pQueueCreateInfos(queueCreateInfo)
            deviceCreateInfo.pEnabledFeatures(physical.features)
            deviceCreateInfo.ppEnabledExtensionNames(enabledExtensions)
            deviceCreateInfo.flags(0)
            deviceCreateInfo.pNext(MemoryUtil.NULL)


            val pp = stack.mallocPointer(1)
            checkVk(VK10.vkCreateDevice(physical.device, deviceCreateInfo, null, pp))
            device = VkDevice(pp[0], physical.device, deviceCreateInfo)

            VK10.vkGetDeviceQueue(device, presentQueueIndex, 0, pp)
            presentQueue = VkQueue(pp[0], device)

            VK10.vkGetDeviceQueue(device, graphicsQueueIndex, 0, pp)
            graphicsQueue = VkQueue(pp[0], device)
        }
    }

    override fun close() {
        VK10.vkDestroyDevice(device, null)
    }
}