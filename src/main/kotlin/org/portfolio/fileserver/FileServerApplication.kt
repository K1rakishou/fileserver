package org.portfolio.fileserver

import com.samskivert.mustache.Mustache
import org.portfolio.fileserver.handlers.TestHandler
import org.portfolio.fileserver.routers.Routes
import org.springframework.boot.autoconfigure.mustache.MustacheResourceTemplateLoader
import org.springframework.boot.web.reactive.result.view.MustacheViewResolver
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.beans
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.ipc.netty.http.server.HttpServer
import reactor.ipc.netty.tcp.BlockingNettyContext

class FileServerApplication(port: Int = 8080) {

    private val httpHandler: HttpHandler
    private val server: HttpServer
    private lateinit var nettyContext: BlockingNettyContext

    init {
        val context = GenericApplicationContext().apply {
            myBeans().initialize(this)
            refresh()
        }

        server = HttpServer.create(port)
        httpHandler = WebHttpHandlerBuilder.applicationContext(context).build()
    }

    fun start() {
        nettyContext = server.start(ReactorHttpHandlerAdapter(httpHandler))
    }

    fun startAndAwait() {
        server.startAndAwait(ReactorHttpHandlerAdapter(httpHandler), { nettyContext = it })
    }

    fun stop() {
        nettyContext.shutdown()
    }
}

fun myBeans() = beans {
    bean<Routes>()
    bean<TestHandler>()
    bean("webHandler") {
        RouterFunctions.toWebHandler(ref<Routes>().myRouter(), HandlerStrategies.builder().viewResolver(ref()).build())
    }
    bean {
        val prefix = "classpath:/templates/"
        val suffix = ".mustache"
        val loader = MustacheResourceTemplateLoader(prefix, suffix)
        MustacheViewResolver(Mustache.compiler().withLoader(loader)).apply {
            setPrefix(prefix)
            setSuffix(suffix)
        }
    }
}

fun main(args: Array<String>) {
    FileServerApplication().startAndAwait()
}
