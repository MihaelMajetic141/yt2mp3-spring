package com.yt2mp3.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class ConvertService {

    private val logger = LoggerFactory.getLogger(ConvertService::class.java)

    suspend fun convertToMp3(
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val process = ProcessBuilder("yt-dlp", "-j", url).start()

        val metadataJson = process.inputStream.bufferedReader().readText()
        val metadata = jacksonObjectMapper().readTree(metadataJson)
        val title = metadata["title"].asText()

        val tempFile = File.createTempFile(title, ".mp3")
        tempFile.delete() // remove, just keep path for directory

        try {
            val processBuilder = ProcessBuilder(
                "yt-dlp",
                "-x", // Extract audio
                "--audio-format", "mp3",
                "--audio-quality", "0", // Best quality
                "-o", tempFile.absolutePath, // "/tmp/%(title)s.%(ext)s"
                url
            )

            val process = processBuilder.start()

            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line ->
                logger.debug("yt-dlp: $line")

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
}