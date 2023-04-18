package com.johnhite.game.vulkan

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

class SimpleVertexBuffer(val buffer: VulkanBuffer) : Vertex, AutoCloseable {
    private val binding = VkVertexInputBindingDescription.calloc(1)
    private val attributes = VkVertexInputAttributeDescription.calloc(2)

    init {
        binding[0]
            .binding(0)
            .stride(5 * 4)
            .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)

        attributes[0]
            .binding(0)
            .location(0)
            .format(VK10.VK_FORMAT_R32G32_SFLOAT)
            .offset(0)

        attributes[1]
            .binding(0)
            .location(1)
            .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
            .offset(2 * 4)
    }

    override fun getBindingDescription(): VkVertexInputBindingDescription.Buffer {
        return binding
    }

    override fun getAttributeDescription(): VkVertexInputAttributeDescription.Buffer {
        return attributes
    }

    override fun close() {
        binding.free()
        attributes.free()
        buffer.close()
    }
}