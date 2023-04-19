package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkSubmitInfo

object Buffers {

    fun createBuffer(device: LogicalDevice, sizeInBytes: Long, usage: Int, sharing: Int, properties: Int) : VulkanBuffer {
        MemoryStack.stackPush().use { stack ->
            val pointer = stack.mallocLong(1)
            val bufferInfo = VkBufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .size(sizeInBytes)
                .usage(usage)
                .sharingMode(sharing)

            checkVk(vkCreateBuffer(device.device, bufferInfo, null, pointer))
            val bufferPointer = pointer[0]

            val memRequirements = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device.device, bufferPointer, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .`sType$Default`()
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(findMemoryType(device.physical, memRequirements.memoryTypeBits(), properties))

            checkVk(vkAllocateMemory(device.device, allocInfo, null, pointer))
            val memoryPointer = pointer[0]

            vkBindBufferMemory(device.device, bufferPointer, memoryPointer, 0)
            return VulkanBuffer(device, bufferPointer, memoryPointer, sizeInBytes)
        }
    }

    fun findMemoryType(device: PhysicalDevice, typeFilter: Int, properties: Int) : Int {
        val types = device.memoryProperties.memoryTypes()
        for (i in 0 until types.capacity()) {
            if (typeFilter and (1 shl i) > 0 && (types.get(i).propertyFlags() and properties) == properties) {
                return i
            }
        }
        throw RuntimeException("Failed to find requested memory type")
    }

    fun copy(src: VulkanBuffer, dst: VulkanBuffer, device: LogicalDevice, pool: Long) {
        MemoryStack.stackPush().use { stack ->
            val commandAlloc = VkCommandBufferAllocateInfo.calloc(stack)
                .`sType$Default`()
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandPool(pool)
                .commandBufferCount(1)
            val ptr = stack.mallocPointer(1)
            vkAllocateCommandBuffers(device.device, commandAlloc, ptr)
            val commandBuffer = VkCommandBuffer(ptr[0], device.device)

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .`sType$Default`()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            vkBeginCommandBuffer(commandBuffer, beginInfo)
            val copyCmd = VkBufferCopy.calloc(1, stack)
            copyCmd[0].srcOffset(0)
                .dstOffset(0)
                .size(src.size)

            vkCmdCopyBuffer(commandBuffer, src.bufferPtr, dst.bufferPtr, copyCmd)
            vkEndCommandBuffer(commandBuffer)

            val submitInfo = VkSubmitInfo.calloc(stack)
                .`sType$Default`()
                .pCommandBuffers(ptr)

            vkQueueSubmit(device.graphicsQueue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(device.graphicsQueue)

            vkFreeCommandBuffers(device.device, pool, commandBuffer)
        }
    }

}