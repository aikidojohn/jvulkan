package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements

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

}