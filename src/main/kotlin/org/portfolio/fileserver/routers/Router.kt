package org.portfolio.fileserver.routers

import org.portfolio.fileserver.handlers.ApiHandler
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.router

class Router(private val apiHandler: ApiHandler) {

    fun setUpRouter() = router {
        "/v1".nest {
            "/api".nest {
                accept(MediaType.MULTIPART_FORM_DATA).nest {
                    POST("/upload", apiHandler::handleFileUpload)
                }
            }
        }
    }
}