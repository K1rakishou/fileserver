package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.portfolio.fileserver.repository.FilesRepository
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

class DownloadFileHandler(private val fs: FileSystem,
                          private val repo: FilesRepository) {

    fun handleFileDownload(request: ServerRequest): Mono<ServerResponse> {
        return ServerResponse.ok().body(Mono.just("123"))
    }
}