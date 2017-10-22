package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.portfolio.fileserver.repository.FilesRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

class DownloadFileHandler(private val fs: FileSystem,
                          private val repo: FilesRepository) {

    private val logger = LoggerFactory.getLogger(DownloadFileHandler::class.java)
    private var fileDirectoryPath = Path(fs.homeDirectory, "files")

    fun handleFileDownload(request: ServerRequest): Mono<ServerResponse> {
        return Mono.just(request.pathVariable("file_name"))
                .flatMap { fileName -> repo.findById(fileName) }
                .flatMap { storedFile ->
                    //FIXME: we need to somehow close the inputs stream after we've read everything, dunno how to do that atm
                    val inputStream = fs.open(Path(fileDirectoryPath, storedFile.newFileName))
                    val bufferList = DataBufferUtils.read(inputStream, DefaultDataBufferFactory(false, 4096), 4096)

                    return@flatMap Mono.zip(ServerResponse.ok().body(bufferList), Mono.just(inputStream))
                }
                .map { it.t1 }
                .onErrorResume { error ->
                    logger.error("Unhandled exception", error)

                    return@onErrorResume ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Mono.just("Something went wrong"))
                }
    }
}