package org.portfolio.fileserver.config

import com.samskivert.mustache.Mustache
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.portfolio.fileserver.handlers.ApiHandler
import org.portfolio.fileserver.routers.Router
import org.springframework.boot.autoconfigure.mustache.MustacheResourceTemplateLoader
import org.springframework.boot.web.reactive.result.view.MustacheViewResolver
import org.springframework.context.support.beans
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunctions

fun myBeans() = beans {
    bean<Router>()
    bean<FileSystem> {
        FileSystem.newInstance(Configuration())
    }
    bean<ApiHandler>()
    bean("webHandler") {
        RouterFunctions.toWebHandler(ref<Router>().setUpRouter(), HandlerStrategies.builder().viewResolver(ref()).build())
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