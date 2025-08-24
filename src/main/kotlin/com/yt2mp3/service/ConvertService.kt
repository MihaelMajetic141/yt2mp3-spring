package com.yt2mp3.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

@Service
class ConvertService {

    private val logger = LoggerFactory.getLogger(ConvertService::class.java)

    suspend fun convertToMp3(
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (url.isEmpty())
            throw IOException("URL is empty")

        val cleanUrl = sanitizeYtUrl(url)
        val process = ProcessBuilder(
            "yt-dlp",
            "--print", "%(title)s",
            cleanUrl
        ).start()

        val title = process.inputStream.bufferedReader().readLine()

//        val metadataJson = reader.readLine() ?: throw RuntimeException("No metadata received")
//
//        val exit = process.waitFor()
//        if (exit != 0) {
//            throw RuntimeException("yt-dlp failed with exit code $exit")
//        }
//
//        // val metadataJson = process.inputStream.bufferedReader().use { it.readText() }
//
//        val metadata = jacksonObjectMapper().readTree(metadataJson)
//        val title = metadata["filename"].asText()

        val tempFile = File.createTempFile(title, ".mp3").apply { delete() }
        try {
            val processBuilder = ProcessBuilder(
                /* ...command = */ "yt-dlp",
                "-x", // Extract audio
                "--audio-format", "mp3",
                "--audio-quality", "0",
                "-o", tempFile.absolutePath, // "/tmp/%(title)s.%(ext)s", // tempFile.absolutePath,
                cleanUrl
            )

            val process = processBuilder.start()

            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line ->
                // logger.debug("yt-dlp: $line")
                val match = Regex("""(\d+(?:\.\d+)?)%""").find(line)
                if (match != null) {
                    val percent = match.groupValues[1].toDouble().toInt()
                    onProgress(percent)
                }
            }
            
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                throw IOException("yt-dlp failed with exit code $exitCode: $errorOutput")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IOException("Downloaded file is empty or does not exist")
            }

            logger.info("Downloaded MP3 file to: ${tempFile.absolutePath}")
            tempFile

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun sanitizeYtUrl(url: String): String {
        val uri = URI(url)

        if (uri.host.contains("youtu.be")) {
            val videoId = uri.path.trimStart('/')
            return "https://www.youtube.com/watch?v=$videoId"
        }

        val query = uri.rawQuery ?: return url
        val queryParameters = query.split("&")
            .mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2)
                    parts[0] to URLDecoder.decode(
                        parts[1],
                        "UTF-8"
                    ) else null
            }.toMap()

        val videoId = queryParameters["v"] ?: return url
        return "https://www.youtube.com/watch?v=$videoId"
    }
}