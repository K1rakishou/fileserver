package org.portfolio.fileserver.routers

import org.portfolio.fileserver.handlers.DownloadFileHandler
import org.portfolio.fileserver.handlers.UploadFileHandler
import org.springframework.http.MediaType
import org.springframework.http.MediaType.TEXT_HTML
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

class Router(private val uploadFileHandler: UploadFileHandler,
             private val downloadFileHandler: DownloadFileHandler) {

    fun setUpRouter() = router {
        accept(TEXT_HTML).nest {
            GET("/") { ServerResponse.ok().render("index") }
        }
        "/v1".nest {
            "/api".nest {
                accept(MediaType.MULTIPART_FORM_DATA).nest {
                    POST("/upload", uploadFileHandler::handleFileUpload)
                }

                accept(MediaType.APPLICATION_JSON).nest {
                    GET("/download/{file_name}", downloadFileHandler::handleFileDownload)
                }
            }
        }
    }
}