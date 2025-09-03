package com.yt2mp3.controller

import com.yt2mp3.component.DownloadRegistry
import com.yt2mp3.service.ConvertService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.util.UUID


@Controller
class ConvertController(
    private val convertService: ConvertService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val downloadRegistry: DownloadRegistry
) {
    private val logger = LoggerFactory.getLogger(ConvertController::class.java)
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + job)

    @MessageMapping("/convert")
    fun handleConvertRequest(
        @Payload url: String,
        @Header("simpSessionId") sessionId: String
    ) {
        if (url.trim().isEmpty()) {
            sendError(sessionId, "Empty URL provided")
            return
        }
        logger.info("Received download request for URL: $url via session: $sessionId")
        coroutineScope.launch {
            try {
                val videoIdAndTitleMap: Map<String, String> = convertService.extractTitle(url)
                messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/videoId",
                    videoIdAndTitleMap["videoId"].toString(),
                    createHeaders(sessionId)
                )
                messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/title",
                    videoIdAndTitleMap["title"].toString(),
                    createHeaders(sessionId)
                )
                val file = convertService.convertToMp3(
                    videoId = videoIdAndTitleMap["videoId"],
                    title = videoIdAndTitleMap["title"]
                ) { progress ->
                    messagingTemplate.convertAndSendToUser(
                        sessionId,
                        "/queue/progress",
                        progress,
                        createHeaders(sessionId)
                    )
                }
                val downloadId = UUID.randomUUID().toString()
                downloadRegistry.register(downloadId, file)
                val downloadUrl = "http://localhost:8080/api/download/$downloadId"

                messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/mp3",
                    downloadUrl,
                    createHeaders(sessionId)
                )

            } catch (e: Exception) {
                logger.error("Error processing download for session $sessionId: ${e.message}", e)
                sendError("sessionId", "Error: ${e.message}")
            }
        }
    }

    private fun sendError(sessionId: String, message: String) {
        messagingTemplate.convertAndSendToUser(
            sessionId,
            "/queue/error",
            message,
            createHeaders(sessionId)
        )
    }

    private fun createHeaders(sessionId: String): MessageHeaders {
        val headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE)
        headerAccessor.sessionId = sessionId
        headerAccessor.setLeaveMutable(true)
        return headerAccessor.messageHeaders
    }

}