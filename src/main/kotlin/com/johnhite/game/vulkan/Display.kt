package com.johnhite.game.vulkan

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack

class Display(width: Int, height: Int, title: String) {
    val window: Long
    var surface: Long = 0
        private set
    var width: Int
        private set
    var height: Int
        private set

    private val resizeListeners = ArrayList<(Long, Int, Int) -> Unit>()

    init {
        this.width = width
        this.height = height
        GLFWErrorCallback.createPrint().set();
        if (!GLFW.glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver")
        }

        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
        window = GLFW.glfwCreateWindow(width, height, title, 0, 0)
        GLFW.glfwSetFramebufferSizeCallback(window) { win: Long, newWidth: Int, newHeight: Int ->
            notifyListeners(win, newWidth, newHeight)
        }
    }

    fun initSurface(context: VulkanContext) {
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(context.instance, window, null, lp)
            surface = lp[0]
        }
    }

    fun addResizeListener(func: (Long, Int, Int) -> Unit) {
        resizeListeners.add(func)
    }

    private fun notifyListeners(window: Long, newWidth: Int, newHeight: Int) {
        for (f in resizeListeners) {
            f(window, newWidth, newHeight)
        }
    }
}