package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.portfolio.fileserver.extensions.extractExtension
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorService
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.io.IOException

class UploadFileHandler(private val fs: FileSystem,
                        private val repo: FilesRepository,
                        private val generator: GeneratorService) {

    private val maxFileSize = 5242880L //5MB
    private val logger = LoggerFactory.getLogger(UploadFileHandler::class.java)
    private var fileDirectoryPath = Path(fs.homeDirectory, "files")
    private val uploadingFilePartName = "file"

    fun handleFileUpload(request: ServerRequest): Mono<ServerResponse> {
        return request.body(BodyExtractors.toMultipartData())
                .map { mvm -> mvm.getFirst(uploadingFilePartName) }
                .map(this::checkRequestContainsFile)
                .flatMap(this::waitForRemainingParts)
                .doOnNext(this::checkFileSize)
                .flatMap(this::writeToStorage)
                .flatMap(this::saveFileInfoToDb)
                .map { it.t2 }
                .flatMap { result -> ServerResponse.ok().body(Mono.just(result)) }
                .onErrorResume(this::handleError)
    }

    private fun checkRequestContainsFile(part: Part?): Pair<Part, String> {
        if (part == null) {
            throw NoFileToUploadException()
        }

        val originalName = (part as FilePart).filename()
        return part to originalName
    }

    private fun waitForRemainingParts(it: Pair<Part, String>): Mono<Tuple2<MutableList<DataBuffer>, String>>? {
        val part = it.first
        val originalName = it.second

        val partsListMono = part.content().buffer().single()
        return Mono.zip(partsListMono, Mono.just(originalName))
    }

    private fun writeToStorage(it: Tuple2<MutableList<DataBuffer>, String>): Mono<FileInfo> {
        val partsList = it.t1
        val originalName = it.t2
        val extension = originalName.extractExtension()
        val generatedName = generator.generateNewFileName()
        val newFileName = "$generatedName.$extension"
        val fullPath = Path(fileDirectoryPath, newFileName)

        fs.create(fullPath).use { outputStream ->
            for (data in partsList) {
                data.asInputStream().use { inputStream ->
                    val chunkSize = inputStream.available()
                    val buffer = ByteArray(chunkSize)

                    inputStream.read(buffer, 0, chunkSize)
                    outputStream.wrappedStream.write(buffer, 0, chunkSize)
                }
            }
        }

        return Mono.just(FileInfo(newFileName, originalName))
    }

    private fun checkFileSize(it: Tuple2<MutableList<DataBuffer>, String>) {
        val dataBufferList = it.t1
        val fileSize: Long = dataBufferList
                .map { it.readableByteCount().toLong() }
                .sum()

        if (fileSize > maxFileSize) {
            throw MaxFilesSizeExceededException(maxFileSize)
        }
    }

    private fun saveFileInfoToDb(fileInfo: FileInfo): Mono<Tuple2<StoredFile, String>> {
        val repoResult = repo.save(StoredFile(fileInfo.newFileName,
                fileInfo.originalName, System.currentTimeMillis()))

        return Mono.zip(repoResult, Mono.just(fileInfo.newFileName))
    }

    private fun handleError(error: Throwable): Mono<ServerResponse> {
        logger.error("Unhandled exception", error)

        return when (error) {
            is NoFileToUploadException -> {
                val msg = error.message ?: "No file to upload received"
                ServerResponse.badRequest().body(Mono.just(msg))
            }
            is IOException -> {
                val msg = error.message ?: "IOException while trying to store the file"
                ServerResponse.unprocessableEntity().body(Mono.just(msg))
            }
            is MaxFilesSizeExceededException -> {
                val msg = error.message ?: "File is too big"
                ServerResponse.badRequest().body(Mono.just(msg))
            }
            else -> {
                val msg = error.message ?: "Unknown error"
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Mono.just(msg))
            }
        }
    }

    data class FileInfo(val newFileName: String,
                        val originalName: String)

    class NoFileToUploadException : Exception("The request does not contain \"file\" part")
    class MaxFilesSizeExceededException(maxfs: Long) : Exception("The size of the file exceeds ${maxfs / (1024 * 1024)} MB")
}





