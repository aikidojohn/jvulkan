package com.johnhite.game.vulkan

import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.IntBuffer

class SwapChain(private val device: LogicalDevice, surface: Long, window: Long) : AutoCloseable {
    val capabilities = VkSurfaceCapabilitiesKHR.malloc()
    val formats: VkSurfaceFormatKHR.Buffer
    val presentModes: IntBuffer
    val format: VkSurfaceFormatKHR
    val presentMode: Int
    val extent = VkExtent2D.malloc()
    val swapchain: Long
    val images = ArrayList<Long>()
    val imageViews = ArrayList<Long>()
    val frameBuffers = ArrayList<Long>()

    init {
        MemoryStack.stackPush().use { stack ->
            val physical = device.device.physicalDevice
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, capabilities)

            val ip = stack.mallocInt(1)
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, ip, null)

            if (ip[0] > 0) {
                formats = VkSurfaceFormatKHR.malloc(ip[0])
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, ip, formats)
            } else {
                throw RuntimeException("Device does not support any surface formats!")
            }

            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, ip, null as IntBuffer?)
            if (ip[0] > 0) {
                presentModes = MemoryUtil.memAllocInt(ip[0])
                KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, ip, presentModes)
            } else {
                throw RuntimeException("Device does not support any present modes!")
            }

            //Select format
            format = formats.stream().filter { f -> f.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB && f.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR }
                .findFirst()
                .orElse(formats[0])

            //Select present mode
            var pm = KHRSurface.VK_PRESENT_MODE_FIFO_KHR
            for (i in 0 until presentModes.capacity()) {
                if (presentModes[i] == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                     pm = KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR
                    break;
                }
            }
            presentMode = pm

            //Select swap extent
            if (capabilities.currentExtent().width() >= Int.MAX_VALUE || capabilities.currentExtent().width() < 0) {
                //High Density Display
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                GLFW.glfwGetFramebufferSize(window, width, height)
                extent.set(
                    Utils.clampInt(
                        width[0],
                        capabilities.minImageExtent().width(),
                        capabilities.maxImageExtent().width()
                    ),
                    Utils.clampInt(
                        height[0],
                        capabilities.minImageExtent().height(),
                        capabilities.maxImageExtent().height()
                    )
                )
            } else {
                extent.set(capabilities.currentExtent().width(), capabilities.currentExtent().height())
            }

            val imageCount = if (capabilities.maxImageCount() == 0 || capabilities.minImageCount() + 1 <= capabilities.maxImageCount()) capabilities.minImageCount() + 1 else capabilities.minImageCount()
            //Create Swapchain
            val swapChainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
            swapChainCreateInfo
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(format.format())
                .imageColorSpace(format.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK10.VK_NULL_HANDLE)

            if (device.queueFamilyIndices.size > 1) {
                val queueFamilies = stack.mallocInt(device.queueFamilyIndices.size)
                for (family in device.queueFamilyIndices) {
                    queueFamilies.put(family)
                }
                queueFamilies.rewind()

                swapChainCreateInfo
                    .imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(device.queueFamilyIndices.size)
                    .pQueueFamilyIndices(queueFamilies)
            }
            else {
                swapChainCreateInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
            }

            //Create swap chain
            val lp = stack.mallocLong(1)
            checkVk(KHRSwapchain.vkCreateSwapchainKHR(device.device, swapChainCreateInfo, null, lp))
            swapchain = lp[0]

            //Get swapchain images
            KHRSwapchain.vkGetSwapchainImagesKHR(device.device, swapchain, ip, null)
            val imagePointers = stack.mallocLong(ip[0])
            KHRSwapchain.vkGetSwapchainImagesKHR(device.device, swapchain, ip, imagePointers)
            for (i in 0 until imagePointers.capacity()) {
                images.add(imagePointers[i])

                //image view
                val imageViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .`sType$Default`()
                    .image(imagePointers[i])
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format.format())
                    .components {
                        it.r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY)
                    }
                    .subresourceRange {
                        it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }
                checkVk(vkCreateImageView(device.device, imageViewInfo, null, lp))
                imageViews.add(lp[0])
            }
        }
    }


    override fun close() {
        KHRSwapchain.vkDestroySwapchainKHR(device.device, swapchain, null)
        for (i in imageViews.indices) {
            vkDestroyImageView(device.device, imageViews[i], null)
        }
        capabilities.free()
        formats.free()
        MemoryUtil.memFree(presentModes)
        extent.free()
    }
}