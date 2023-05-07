package com.johnhite.game.vulkan

import com.johnhite.game.vulkan.shader.Shader
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR
import org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT

class GraphicsPipelineBuilder(private val stack : MemoryStack = MemoryStack.stackPush()) : AutoCloseable {
    private var dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
    private var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
    private var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
    private var viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
    private var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
    private var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
    private var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
    private var colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
    private var layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack)
    private lateinit var shaderStageCreateInfo: VkPipelineShaderStageCreateInfo.Buffer
    private lateinit var renderPass: RenderPass

    fun dynamicStates(states: IntArray) : GraphicsPipelineBuilder{
        val dynStates = stack.mallocInt(states.size)
        dynStates.put(states)
        dynStates.rewind()
        dynamicState.pDynamicStates(dynStates)
        return this
    }

    fun withDynamicStates(states: VkPipelineDynamicStateCreateInfo) : GraphicsPipelineBuilder{
        dynamicState.free()
        dynamicState = states
        return this
    }

    fun vertexInput(vertexBuffer: Vertex) : GraphicsPipelineBuilder {
        vertexInput
            .`sType$Default`()
            .pVertexBindingDescriptions(vertexBuffer.getBindingDescription())
            .pVertexAttributeDescriptions(vertexBuffer.getAttributeDescription())
        return this
    }

    fun withInputAssembly(assembly: VkPipelineInputAssemblyStateCreateInfo) : GraphicsPipelineBuilder{
        inputAssembly.free()
        inputAssembly = assembly
        return this
    }

    fun inputTopology(topology: Int) : GraphicsPipelineBuilder {
        inputAssembly.topology(topology)
        return this
    }
    fun withViewport(viewport: VkPipelineViewportStateCreateInfo) : GraphicsPipelineBuilder{
        viewportState.free()
        viewportState = viewport
        return this
    }
    fun viewportCount(count: Int) : GraphicsPipelineBuilder {
        viewportState.viewportCount(1)
        return this
    }
    fun scissorCount(count: Int) : GraphicsPipelineBuilder {
        viewportState.viewportCount(1)
        return this
    }
    fun withRasterization(rasterizerState: VkPipelineRasterizationStateCreateInfo) : GraphicsPipelineBuilder {
        rasterizer.free()
        rasterizer = rasterizerState
        return this
    }
    fun depthClampEnabled(enabled: Boolean) : GraphicsPipelineBuilder {
        rasterizer.depthClampEnable(enabled)
        return this
    }
    fun rasterizerDiscardEnabled(enabled: Boolean) : GraphicsPipelineBuilder {
        rasterizer.rasterizerDiscardEnable(enabled)
        return this
    }
    fun polygonMode(mode: Int) : GraphicsPipelineBuilder {
        rasterizer.polygonMode(mode)
        return this
    }
    fun lineWidth(width: Float) : GraphicsPipelineBuilder {
        rasterizer.lineWidth(width)
        return this
    }
    fun cullMode(mode: Int) : GraphicsPipelineBuilder {
        rasterizer.cullMode(mode)
        return this
    }
    fun frontFace(face: Int) : GraphicsPipelineBuilder {
        rasterizer.frontFace(face)
        return this
    }
    fun depthBias(constantFactor: Float, slopeFactor: Float, clamp: Float) : GraphicsPipelineBuilder {
        rasterizer
            .depthBiasEnable(true)
            .depthBiasClamp(clamp)
            .depthBiasConstantFactor(constantFactor)
            .depthBiasSlopeFactor(slopeFactor)
        return this
    }

    fun withMultisampling(state: VkPipelineMultisampleStateCreateInfo) : GraphicsPipelineBuilder {
        multisampling.free()
        multisampling = state
        return this
    }

    fun sampleShadingEnable(enabled: Boolean) : GraphicsPipelineBuilder {
        multisampling.sampleShadingEnable(enabled)
        return this
    }
    fun alphaToCoverageEnable(enabled: Boolean) : GraphicsPipelineBuilder {
        multisampling.alphaToCoverageEnable(enabled)
        return this
    }
    fun alphaToOneEnable(enabled: Boolean) : GraphicsPipelineBuilder {
        multisampling.alphaToOneEnable(enabled)
        return this
    }
    fun rasterizationSamples(samplesFlag: Int) : GraphicsPipelineBuilder {
        multisampling.rasterizationSamples(samplesFlag)
        return this
    }
    fun minSampleShading(min: Float) : GraphicsPipelineBuilder {
        multisampling.minSampleShading(min)
        return this
    }

    fun withColorBlendAttachments(attachments: VkPipelineColorBlendAttachmentState.Buffer) : GraphicsPipelineBuilder {
        colorBlendAttachment.free()
        colorBlendAttachment = attachments
        return this
    }

    fun withColorBlendAttachments(blend: VkPipelineColorBlendStateCreateInfo) : GraphicsPipelineBuilder {
        colorBlend.free()
        colorBlend = blend
        return this
    }

    fun blendConstants(constants: FloatArray) : GraphicsPipelineBuilder {
        val blendConstants = stack.mallocFloat(constants.size)
        blendConstants.put(constants)
        blendConstants.rewind()
        colorBlend.blendConstants(blendConstants)
        return this
    }

    fun withLayoutBindings(bindings: VkDescriptorSetLayoutBinding.Buffer) : GraphicsPipelineBuilder {
        layoutBindings.free()
        layoutBindings = bindings
        return this
    }

    fun withShaderStages(shaders: List<Shader>) : GraphicsPipelineBuilder {
        shaderStageCreateInfo = VkPipelineShaderStageCreateInfo.calloc(shaders.size, stack)
        for ((i, shader) in shaders.withIndex()) {
            shaderStageCreateInfo[i]
                .`sType$Default`()
                .stage(shader.stage)
                .module(shader.id)
                .pName(stack.ASCII("main")) //TODO add to shader
        }
        return this
    }

    fun withRenderPass(renderPass: RenderPass) : GraphicsPipelineBuilder {
        this.renderPass = renderPass
        return this
    }

    fun build(device: LogicalDevice) : Pipeline {
        val descriptorLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
            .`sType$Default`()
            .pBindings(layoutBindings)
        val lp2 = stack.mallocLong(1)
        checkVk(VK10.vkCreateDescriptorSetLayout(device.device, descriptorLayoutInfo, null, lp2))
        val descriptorSetLayouts = LongArray(lp2.capacity())
        for (i in 0 until lp2.capacity()) {
            descriptorSetLayouts[i] = lp2[i]
        }

        //create pipeline
        val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
            .`sType$Default`()
            .pSetLayouts(lp2)

        val lp = stack.mallocLong(1)
        checkVk(VK10.vkCreatePipelineLayout(device.device, pipelineLayoutInfo, null, lp))
        val pipelineLayout = lp[0]

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

        checkVk(VK10.vkCreateGraphicsPipelines(device.device, VK10.VK_NULL_HANDLE, graphicsPipelineCreate, null, lp))
        val graphicsPipeline = lp[0]
        return Pipeline(device, graphicsPipeline, pipelineLayout,  descriptorSetLayouts)
    }

    init {
        val dynStates = stack.mallocInt(2)
        dynStates.put(intArrayOf(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR))
        dynStates.rewind()

        dynamicState.`sType$Default`()
            .pDynamicStates(dynStates)

        vertexInput.`sType$Default`()
        inputAssembly.`sType$Default`()
            .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            .primitiveRestartEnable(false)

        viewportState.`sType$Default`()
            .viewportCount(1)
            .scissorCount(1)

        rasterizer.`sType$Default`()
            .depthClampEnable(false)
            .rasterizerDiscardEnable(false)
            .polygonMode(VK10.VK_POLYGON_MODE_FILL)
            .lineWidth(1.0f)
            .cullMode(VK10.VK_CULL_MODE_BACK_BIT)
            .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
            .depthBiasEnable(false)
            .depthBiasConstantFactor(0f)
            .depthBiasClamp(0f)
            .depthBiasSlopeFactor(0f)

        multisampling.`sType$Default`()
            .sampleShadingEnable(false)
            .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)
            .minSampleShading(1f)
            .alphaToCoverageEnable(false)
            .alphaToOneEnable(false)

        colorBlendAttachment[0].colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT or VK10.VK_COLOR_COMPONENT_G_BIT or VK10.VK_COLOR_COMPONENT_B_BIT or VK10.VK_COLOR_COMPONENT_A_BIT)
            .blendEnable(false)
            .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
            .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ZERO)
            .colorBlendOp(VK10.VK_BLEND_OP_ADD)
            .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
            .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO)
            .alphaBlendOp(VK10.VK_BLEND_OP_ADD)


        val blendConstants = stack.mallocFloat(4)
        blendConstants.put(floatArrayOf(0f, 0f,0f,0f))
        blendConstants.rewind()
        colorBlend.`sType$Default`()
            .logicOpEnable(false)
            .logicOp(VK10.VK_LOGIC_OP_COPY)
            .attachmentCount(1)
            .pAttachments(colorBlendAttachment)
            .blendConstants(blendConstants)


    }

    override fun close() {
        stack.close()
    }
}