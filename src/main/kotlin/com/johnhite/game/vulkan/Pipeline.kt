package com.johnhite.game.vulkan

import com.johnhite.game.vulkan.shader.Shader
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSubpassDescription

class Pipeline(private val device: LogicalDevice, val graphicsPipeline: Long, val pipelineLayout: Long, val bufferLayouts: LongArray) : AutoCloseable {

    /*init {
        //TODO make this configurable. Multiple pipelines with different configurations will be required for most applications
        MemoryStack.stackPush().use { stack ->
            val shaderStageCreateInfo = VkPipelineShaderStageCreateInfo.calloc(shaders.size, stack)
            for ((i, shader) in shaders.withIndex()) {
                shaderStageCreateInfo[i]
                    .`sType$Default`()
                    .stage(shader.stage)
                    .module(shader.id)
                    .pName(stack.ASCII("main")) //TODO add to shader
            }

            val dynStates = stack.mallocInt(2)
            dynStates.put(intArrayOf(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR))
            dynStates.rewind()

            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .pDynamicStates(dynStates)

            val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack) //TODO define in shader?
                .`sType$Default`()
                .pVertexBindingDescriptions(vertexBuffer.getBindingDescription())
                .pVertexAttributeDescriptions(vertexBuffer.getAttributeDescription())

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)

            val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .viewportCount(1)
                .scissorCount(1)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
                .depthBiasConstantFactor(0f)
                .depthBiasClamp(0f)
                .depthBiasSlopeFactor(0f)

            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1f)
                .alphaToCoverageEnable(false)
                .alphaToOneEnable(false)

            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
            colorBlendAttachment[0].colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
                .blendEnable(false)
                .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                .alphaBlendOp(VK_BLEND_OP_ADD)


            val blendConstants = stack.mallocFloat(4)
            blendConstants.put(floatArrayOf(0f, 0f,0f,0f))
            blendConstants.rewind()
            val colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .logicOpEnable(false)
                .logicOp(VK_LOGIC_OP_COPY)
                .attachmentCount(1)
                .pAttachments(colorBlendAttachment)
                .blendConstants(blendConstants)

            //Create Uniform Buffer Layout //TODO define in shaders?
            val uboLayoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
            uboLayoutBinding[0].binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)

            val uboDescriptorCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .`sType$Default`()
                .pBindings(uboLayoutBinding)
            val lp2 = stack.mallocLong(1)
            checkVk(vkCreateDescriptorSetLayout(device.device, uboDescriptorCreateInfo, null, lp2))
            this.uboLayout = lp2[0]

            //create pipeline
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .`sType$Default`()
                .pSetLayouts(lp2)

            val lp = stack.mallocLong(1)
            checkVk(vkCreatePipelineLayout(device.device, pipelineLayoutInfo, null, lp))
            pipelineLayout = lp[0]

            val graphicsPipelineCreate = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .`sType$Default`()
                .stageCount(2)
                .pStages(shaderStageCreateInfo)
                .pVertexInputState(vertexInput)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pColorBlendState(colorBlend)
                .pDynamicState(dynamicState)
                .layout(pipelineLayout)
                .renderPass(renderPass.renderPass)
                .subpass(0)

            checkVk(vkCreateGraphicsPipelines(device.device, VK_NULL_HANDLE, graphicsPipelineCreate, null, lp))
            graphicsPipeline = lp[0]
        }
    }*/

    override fun close() {
        vkDestroyPipeline(device.device, graphicsPipeline, null)
        vkDestroyPipelineLayout(device.device, pipelineLayout, null)
        for (descriptor in bufferLayouts) {
            vkDestroyDescriptorSetLayout(device.device, descriptor, null)
        }
        /*for (shader in shaders) {
            shader.close()
        }*/
    }
}