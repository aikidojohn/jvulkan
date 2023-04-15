package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*

class RenderPass(private val device: LogicalDevice, swapChain: SwapChain) : AutoCloseable {

    val renderPass: Long
    private var closed = false

    init {
        MemoryStack.stackPush().use { stack ->
            val colorAttachment = VkAttachmentDescription.calloc(1, stack)
            colorAttachment[0].format(swapChain.format.format())
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            val colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
            colorAttachmentRef[0].attachment(0)
                .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val subpass = VkSubpassDescription.calloc(1, stack)
            subpass[0].pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorAttachmentRef)

            val dependencies = VkSubpassDependency.calloc(1, stack)
            dependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstStageMask(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

            val renderpass = VkRenderPassCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(colorAttachment)
                .pSubpasses(subpass)
                .pDependencies(dependencies)

            val lp  = stack.mallocLong(1)
            checkVk(VK10.vkCreateRenderPass(device.device, renderpass, null, lp))
            renderPass = lp[0]
        }
    }
    override fun close() {
        if (!closed) {
            VK10.vkDestroyRenderPass(device.device, renderPass, null)
            closed = true
        }
    }
}