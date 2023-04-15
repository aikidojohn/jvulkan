package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.vkCreateFramebuffer
import org.lwjgl.vulkan.VK10.vkDestroyFramebuffer
import org.lwjgl.vulkan.VkFramebufferCreateInfo

class FrameBuffer(private val device: LogicalDevice, renderPass: RenderPass, swapChain: SwapChain, imageIndex: Int): AutoCloseable {
    val frameBuffer: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val attachments = stack.mallocLong(1)
            attachments.put(swapChain.imageViews[imageIndex])
            attachments.rewind()

            val frameBufferInfo = VkFramebufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(renderPass.renderPass)
                .attachmentCount(1)
                .pAttachments(attachments)
                .width(swapChain.extent.width())
                .height(swapChain.extent.height())
                .layers(1)
            val lp = stack.mallocLong(1)
            checkVk(vkCreateFramebuffer(device.device, frameBufferInfo, null, lp))
            frameBuffer = lp[0]
        }
    }

    override fun close() {
        vkDestroyFramebuffer(device.device, frameBuffer, null)
    }
}