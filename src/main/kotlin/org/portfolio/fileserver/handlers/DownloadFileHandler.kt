package org.portfolio.fileserver.handlers

import org.portfolio.fileserver.model.ServerResponseCode
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.model.response.BaseServerResponse
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
import java.io.File

class DownloadFileHandler(private val repo: FilesRepository) {

    private val logger = LoggerFactory.getLogger(DownloadFileHandler::class.java)
    private val readChuckSize = 16384
    private val fileNamePathVariable = "file_name"
    private var fileDirectoryPath = "D:\\files"

    fun handleFileDownload(request: ServerRequest): Mono<ServerResponse> {
        return Mono.just(request.pathVariable(fileNamePathVariable))
                .flatMap(this::fetchFileInfoFromRepo)
                .doOnSuccess(this::checkFileFound)
                .flatMap(this::serveFile)
                .onErrorResume(this::handleErrors)
    }

    private fun fetchFileInfoFromRepo(fileName: String): Mono<StoredFile> {
        return repo.findById(fileName)
                .switchIfEmpty(Mono.just(StoredFile.empty()))
    }

    private fun checkFileFound(storedFile: StoredFile) {
        if (storedFile.isEmpty()) {
            throw CouldNotFindFileException()
        }
    }

    private fun serveFile(storedFile: StoredFile): Mono<ServerResponse> {
        val bufferFlux = Flux.using({
            val path = "$fileDirectoryPath\\${storedFile.newFileName}"
            return@using File(path).inputStream()
        }, { inputStream ->
            return@using DataBufferUtils.read(inputStream, DefaultDataBufferFactory(false, readChuckSize), readChuckSize)
        }, { inputStream ->
            inputStream.close()
        })

        return ServerResponse.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=${storedFile.oldFileName}")
                .body(bufferFlux)
    }

    private fun handleErrors(error: Throwable): Mono<ServerResponse> {
        return when (error) {
            is CouldNotFindFileException -> {
                ServerResponse.status(HttpStatus.NOT_FOUND)
                        .body(Mono.just(BaseServerResponse(ServerResponseCode.FILE_NOT_FOUND.value, error.message!!)))
            }

            else -> {
                logger.error("Unhandled exception", error)
                val msg = error.message ?: "Unknown error"
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Mono.just(BaseServerResponse(ServerResponseCode.UNKNOWN_ERROR.value, msg)))
            }
        }
    }

    class CouldNotFindFileException : Exception("Could not find the file")
}


























