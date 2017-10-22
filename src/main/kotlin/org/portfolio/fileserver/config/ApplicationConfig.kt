package org.portfolio.fileserver.config

import com.mongodb.ConnectionString
import com.samskivert.mustache.Mustache
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.portfolio.fileserver.handlers.UploadFileHandler
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.routers.Router
import org.portfolio.fileserver.service.GeneratorService
import org.springframework.boot.autoconfigure.mustache.MustacheResourceTemplateLoader
import org.springframework.boot.web.reactive.result.view.MustacheViewResolver
import org.springframework.context.support.beans
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunctions

fun myBeans() = beans {
    bean<Router>()
    bean<UploadFileHandler>()
    bean<GeneratorService>()
    bean {
        FilesRepository(ref())
    }
    bean {
        ReactiveMongoRepositoryFactory(ref())
    }
    bean {
        ReactiveMongoTemplate(
                SimpleReactiveMongoDatabaseFactory(
                        ConnectionString("mongodb://192.168.99.100:27017/fileserver")
                )
        )
    }
    bean<FileSystem> {
        FileSystem.newInstance(Configuration())
    }
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