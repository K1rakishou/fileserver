package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.portfolio.fileserver.extensions.extractExtension
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorService
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.io.IOException

class ApiHandler(private val fs: FileSystem,
                 private val repo: FilesRepository,
                 private val generator: GeneratorService) {

    private val logger = LoggerFactory.getLogger(ApiHandler::class.java)
    private var fileDirectoryPath: Path
    private val uploadingFilePartName = "file"

    init {
        fileDirectoryPath = Path(fs.homeDirectory, "files")
    }

    fun handleFileUpload(request: ServerRequest): Mono<ServerResponse> {
        return request.body(BodyExtractors.toMultipartData())
                .map { mvm -> mvm.getFirst(uploadingFilePartName) }
                .map(this::checkRequestContainsFile)
                .flatMap(this::waitForRemainingParts)
                .flatMap(this::writeToStorage)
                .flatMap(this::saveFileInfoToDb)
                .flatMap { ServerResponse.ok().body(Mono.just("ok")) }
                .onErrorResume(this::handleError)
    }

    private fun saveFileInfoToDb(fileInfo: FileInfo): Mono<StoredFile> {
        return repo.save(StoredFile(fileInfo.newFileName,
                fileInfo.originalName, System.currentTimeMillis()))
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

    private fun checkRequestContainsFile(part: Part?): Pair<Part, String> {
        if (part == null) {
            throw NullPointerException()
        }

        val originalName = (part as FilePart).filename()
        return part to originalName
    }

    private fun handleError(error: Throwable): Mono<ServerResponse> {
        logger.error("Unhandled exception", error)

        return when (error) {
            is NullPointerException -> ServerResponse.badRequest().body(Mono.just("No file to upload received"))
            is IOException -> ServerResponse.unprocessableEntity().body(Mono.just("IOException while trying to store the file"))
            else -> throw error
        }
    }

    data class FileInfo(val newFileName: String,
                        val originalName: String)
}
















