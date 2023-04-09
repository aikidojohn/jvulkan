package com.johnhite.game.vulkan

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.lang.StringBuilder

object Resources {
    fun getFile(resourcePath: String) : File {
        var res = Resources.javaClass.getResource(resourcePath)
        if (res == null) {
            res = ClassLoader.getSystemClassLoader().getResource(resourcePath)
        }
        if (res == null) {
            throw FileNotFoundException("Unable to locate resource: $resourcePath")
        }
        return File(res.file)
    }

    fun read(file: File) : String {
        if (!file.absoluteFile.exists()) {
            throw FileNotFoundException("Unable to locate resource: ${file.absoluteFile}")
        }
        return readStream(file.absoluteFile.inputStream())
    }

    fun read(resourcePath: String) : String {
        var istream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)
        if (istream == null) {
            istream = Resources.javaClass.getResourceAsStream(resourcePath)
        }
        if (istream == null) {
            throw FileNotFoundException("Unable to locate resource: $resourcePath")
        }
        return readStream(istream)
    }

    fun readStream(istream: InputStream) : String {
        istream.use { stream ->
            val sb = StringBuilder()
            val buffer = ByteArray(4096)
            var read = stream.read(buffer)
            do {
                sb.append(String(buffer, 0, read ))
                read = stream.read(buffer)
            } while (read > 0)
            return sb.toString()
        }
    }
}