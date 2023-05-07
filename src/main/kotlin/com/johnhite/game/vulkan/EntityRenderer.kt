package com.johnhite.game.vulkan

import com.johnhite.game.vulkan.shader.Shader
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding

class EntityRenderer(private val device: LogicalDevice, private val renderPass: RenderPass, private val vertexInput: Vertex) : AutoCloseable {
    private val shaders = listOf(
        Shader.load(device, "shaders/default.vert"),
        Shader.load(device, "shaders/default.frag")
    )
    private val pipeline: Pipeline

    init {
        MemoryStack.stackPush().use { stack ->
            pipeline = GraphicsPipelineBuilder().use {
                val uboLayoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                uboLayoutBinding[0].binding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)

                it.withRenderPass(renderPass)
                    .withShaderStages(shaders)
                    .withLayoutBindings(uboLayoutBinding)
                    .vertexInput(vertexInput)
                    .build(device)
            }
        }
    }

    fun render() {

    }

    override fun close() {
        for (shader in shaders) {
            shader.close()
        }
    }
}