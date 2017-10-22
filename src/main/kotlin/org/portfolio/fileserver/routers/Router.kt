package org.portfolio.fileserver.routers

import org.portfolio.fileserver.handlers.UploadFileHandler
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.router

class Router(private val uploadFileHandler: UploadFileHandler) {

    fun setUpRouter() = router {
        "/v1".nest {
            "/api".nest {
                accept(MediaType.MULTIPART_FORM_DATA).nest {
                    POST("/upload", uploadFileHandler::handleFileUpload)
                }
            }
        }
    }
}