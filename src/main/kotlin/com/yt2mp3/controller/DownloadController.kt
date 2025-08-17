package com.yt2mp3.controller

import com.yt2mp3.component.DownloadRegistry
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files


@RestController
@RequestMapping("/api")
class DownloadController(
    private val downloadRegistry: DownloadRegistry
) {
    private val logger = LoggerFactory.getLogger(DownloadController::class.java)

    @GetMapping("/download/{id}")
    fun downloadFile(
        @PathVariable id: String,
        response: HttpServletResponse
    ) {
        val file: File = downloadRegistry.get(id)
            ?: throw IllegalArgumentException("Invalid or expired download ID: $id")

        response.contentType = "audio/mpeg"
        response.setHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
        Files.copy(file.toPath(), response.outputStream)

        logger.info("Served MP3 download id=$id (${file.length()} bytes)")

        file.delete()
        downloadRegistry.remove(id)
    }

}