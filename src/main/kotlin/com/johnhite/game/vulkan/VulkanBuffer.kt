package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

class VulkanBuffer (val device: LogicalDevice, val bufferPtr: Long, val memPtr: Long, val size: Long) : AutoCloseable {

    fun map() : Long {
        MemoryStack.stackPush().use { stack ->
            val ptr = stack.mallocPointer(1)
            vkMapMemory(device.device, memPtr, 0, size, 0, ptr)
            return ptr[0]
        }
    }

    fun unmap() {
        vkUnmapMemory(device.device, memPtr)
    }

    fun mapByteBuffer() : ByteBuffer {
        MemoryStack.stackPush().use { stack ->
            val ptr = stack.mallocPointer(1)
            vkMapMemory(device.device, memPtr, 0, size, 0, ptr)
            return MemoryUtil.memByteBuffer(ptr[0], size.toInt())
        }
    }

    fun mapFloatBuffer() : FloatBuffer {
        MemoryStack.stackPush().use { stack ->
            val ptr = stack.mallocPointer(1)
            vkMapMemory(device.device, memPtr, 0, size, 0, ptr)
            return MemoryUtil.memFloatBuffer(ptr[0], (size / 4L).toInt())
        }
    }

    fun mapIntBuffer() : IntBuffer {
        MemoryStack.stackPush().use { stack ->
            val ptr = stack.mallocPointer(1)
            vkMapMemory(device.device, memPtr, 0, size, 0, ptr)
            return MemoryUtil.memIntBuffer(ptr[0], (size / 4L).toInt())
        }
    }

    override fun close() {
        vkDestroyBuffer(device.device, bufferPtr, null)
        vkFreeMemory(device.device, memPtr, null)
    }
}