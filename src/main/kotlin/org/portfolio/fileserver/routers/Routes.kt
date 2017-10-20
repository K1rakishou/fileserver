package org.portfolio.fileserver.routers

import org.portfolio.fileserver.handlers.TestHandler
import org.springframework.web.reactive.function.server.router

class Routes(private val testHandler: TestHandler) {

    fun myRouter() = router {
        GET("/", testHandler::handle)
    }
}