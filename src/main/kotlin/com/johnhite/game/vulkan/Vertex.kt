package com.johnhite.game.vulkan

import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

interface Vertex {
    fun getBindingDescription() : VkVertexInputBindingDescription.Buffer
    fun getAttributeDescription(): VkVertexInputAttributeDescription.Buffer
}