package com.johnhite.game.vulkan

import com.johnhite.game.vulkan.shader.Shader
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
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

class Pipeline(val device: LogicalDevice, val renderPass: RenderPass, val vertexBuffer: Vertex) : AutoCloseable {
    val vertexShader = Shader.load(device, "shaders/default.vert")
    val fragmentShader = Shader.load(device, "shaders/default.frag")
    val graphicsPipeline: Long
    val pipelineLayout: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val shaderStageCreateInfo = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            shaderStageCreateInfo[0]
                .`sType$Default`()
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexShader.id)
                .pName(stack.ASCII("main"))

            shaderStageCreateInfo[1]
                .`sType$Default`()
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShader.id)
                .pName(stack.ASCII("main"))

            val dynStates = stack.mallocInt(2)
            dynStates.put(intArrayOf(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR))
            dynStates.rewind()

            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .pDynamicStates(dynStates)

            val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
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
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
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

            //create pipeline
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .`sType$Default`()

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
    }

    override fun close() {
        vkDestroyPipeline(device.device, graphicsPipeline, null)
        vkDestroyPipelineLayout(device.device, pipelineLayout, null)
        vertexShader.close()
        fragmentShader.close()
    }
}