package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.repository.FilesRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class DownloadFileHandler(private val fs: FileSystem,
                          private val repo: FilesRepository) {

    private val logger = LoggerFactory.getLogger(DownloadFileHandler::class.java)
    private val fileNamePathVariable = "file_name"
    private var fileDirectoryPath = Path(fs.homeDirectory, "files")

    fun handleFileDownload(request: ServerRequest): Mono<ServerResponse> {
        return Mono.just(request.pathVariable(fileNamePathVariable))
                .flatMap(this::fetchFileInfoFromRepo)
                .doOnSuccess(this::checkFileFound)
                .flatMap(this::serveFile)
                .onErrorResume(this::handleErrors)
    }

    private fun fetchFileInfoFromRepo(fileName: String): Mono<StoredFile> {
        return repo.findById(fileName).switchIfEmpty(Mono.just(StoredFile.empty()))
    }

    private fun checkFileFound(storedFile: StoredFile) {
        if (storedFile.isEmpty()) {
            throw CouldNotFindFile()
        }
    }

    private fun serveFile(storedFile: StoredFile): Mono<ServerResponse> {
        val bufferFlux = Flux.using({
            return@using fs.open(Path(fileDirectoryPath, storedFile.newFileName))
        }, { inputStream ->
            return@using DataBufferUtils.read(inputStream, DefaultDataBufferFactory(false, 4096), 4096)
        }, { inputStream ->
            inputStream.close()
        })

        return ServerResponse.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${storedFile.oldFileName}")
                .body(bufferFlux)
    }

    private fun handleErrors(error: Throwable): Mono<ServerResponse> {
        return when (error) {
            is CouldNotFindFile -> {
                ServerResponse.status(HttpStatus.NOT_FOUND).body(Mono.just(error.message!!))
            }

            else -> {
                logger.error("Unhandled exception", error)
                val msg = error.message ?: "Unknown error"
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Mono.just(msg))
            }
        }
    }

    class CouldNotFindFile : Exception("Could not find the file")
}


























