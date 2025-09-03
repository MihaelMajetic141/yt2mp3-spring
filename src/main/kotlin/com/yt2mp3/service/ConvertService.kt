package com.yt2mp3.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

@Service
class ConvertService {

    private val logger = LoggerFactory.getLogger(ConvertService::class.java)

    suspend fun convertToMp3(
        videoId: String?,
        title: String?,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (videoId?.isEmpty() == true || title?.isEmpty() == true)
            throw IOException("Function parameter is empty: \nvideoId=$videoId \ntitle=$title")

        try {
            val url = "https://youtube.com/watch?v=$videoId"
            val tempFile = File.createTempFile(title!!, ".mp3").apply { delete() }

            val convertToMp3Process = ProcessBuilder(
                "/venv/bin/yt-dlp",
                "-x",
                "--audio-format", "mp3",
                "--audio-quality", "0",
                "-o", tempFile.absolutePath,
                url
            ).start()
            logger.info("convert to mp3 process started")

            val reader = convertToMp3Process.inputStream.bufferedReader()
            logger.info("reader defined")

            reader.forEachLine { line ->
                val match = Regex("""(\d+(?:\.\d+)?)%""").find(line)
                if (match != null) {
                    val percent = match.groupValues[1].toDouble().toInt()
                    onProgress(percent)
                }
            }
            val exitCode = convertToMp3Process.waitFor()
            if (exitCode != 0) {
                val errorOutput = convertToMp3Process.errorStream.bufferedReader().readText()
                throw IOException("yt-dlp failed with exit code $exitCode: $errorOutput")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IOException("Downloaded file is empty or does not exist")
            }

            logger.info("Downloaded MP3 file to: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun extractTitle(
        url: String
    ): Map<String, String> {
        val videoIdAndTitleMap: MutableMap<String, String> = mutableMapOf()

        val videoId = getIdFromUrl(url)
        val cleanUrl = "https://youtube.com/watch?v=${videoId}"
        val extractTitleProcess = ProcessBuilder(
            "/venv/bin/yt-dlp",
            "--print", "%(title)s",
            cleanUrl
        ).start()
        val title = extractTitleProcess.inputStream.bufferedReader().readLine()
        videoIdAndTitleMap.put("title", title)
        videoIdAndTitleMap.put("videoId", videoId)

        return videoIdAndTitleMap
    }

    private fun getIdFromUrl(url: String): String {
        val uri = URI(url)

        if (uri.host.contains("youtu.be")) {
            val videoId = uri.path.trimStart('/')
            return videoId
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
        return videoId
    }
}