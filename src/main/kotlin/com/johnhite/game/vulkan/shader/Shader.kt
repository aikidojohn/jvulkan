package com.johnhite.game.vulkan.shader

import com.johnhite.game.vulkan.Resources
import org.lwjgl.util.shaderc.Shaderc
import java.io.File
import java.nio.ByteBuffer

class Shader {

    companion object {
        fun compile(shaderSrc: File) : ByteBuffer {
            var compiler: Long? = null
            var result: Long? = null
            try {
                compiler = Shaderc.shaderc_compiler_initialize()

                val kind = senseShaderKind(shaderSrc)
                val source = Resources.read(shaderSrc)

                result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, shaderSrc.name, "main", 0)
                val status = Shaderc.shaderc_result_get_compilation_status(result)
                if (status != Shaderc.shaderc_compilation_status_success) {
                    val msg = Shaderc.shaderc_result_get_error_message(result)
                    when (status) {
                        Shaderc.shaderc_compilation_status_compilation_error -> throw RuntimeException("Failed to compile shader: Compilation Error: $msg")
                        Shaderc.shaderc_compilation_status_configuration_error -> throw RuntimeException("Failed to compile shader: Configuration Error: $msg")
                        Shaderc.shaderc_compilation_status_internal_error -> throw RuntimeException("Failed to compile shader: Internal Error: $msg")
                        Shaderc.shaderc_compilation_status_invalid_assembly -> throw RuntimeException("Failed to compile shader: Invalid Assembly: $msg")
                        Shaderc.shaderc_compilation_status_invalid_stage -> throw RuntimeException("Failed to compile shader: Invalid Stage: $msg")
                        Shaderc.shaderc_compilation_status_null_result_object -> throw RuntimeException("Failed to compile shader: Null Result Object: $msg")
                        Shaderc.shaderc_compilation_status_transformation_error -> throw RuntimeException("Failed to compile shader: Transformation Error: $msg")
                        Shaderc.shaderc_compilation_status_validation_error -> throw RuntimeException("Failed to compile shader: Validation Error: $msg")
                    }
                }

                return Shaderc.shaderc_result_get_bytes(result) ?: throw RuntimeException("Failed to read shader compilation result")
            }
            finally {
                if (result != null) {
                    Shaderc.shaderc_result_release(result)
                }
                if (compiler != null) {
                    Shaderc.shaderc_compiler_release(compiler)
                }
            }
        }

        private fun senseShaderKind(shaderSrc: File) : Int {
            return when(shaderSrc.extension) {
                "vert" -> Shaderc.shaderc_glsl_vertex_shader
                "frag" -> Shaderc.shaderc_glsl_fragment_shader
                "tesc" -> Shaderc.shaderc_glsl_tess_control_shader
                "tese" -> Shaderc.shaderc_glsl_tess_evaluation_shader
                "geom" -> Shaderc.shaderc_glsl_geometry_shader
                "comp" -> Shaderc.shaderc_glsl_compute_shader
                else -> Shaderc.shaderc_glsl_infer_from_source
            }
        }
    }
}

fun main(args: Array<String>) {
    val vert = Shader.compile(Resources.getFile("shaders/default.vert"))
    val frag = Shader.compile(Resources.getFile("shaders/default.frag"))
    println(vert.capacity())
    println(frag.capacity())
}