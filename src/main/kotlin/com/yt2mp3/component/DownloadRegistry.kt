package com.yt2mp3.component

import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Component
class DownloadRegistry {
    private val files = ConcurrentHashMap<String, File>()

    fun register(id: String, file: File) {
        files[id] = file
    }

    fun get(id: String): File? = files[id]

    fun remove(id: String) {
        files.remove(id)
    }
}