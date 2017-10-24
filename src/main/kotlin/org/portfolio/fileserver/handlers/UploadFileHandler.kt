package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.portfolio.fileserver.extensions.extractExtension
import org.portfolio.fileserver.model.ServerResponseCode
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.model.response.UploadFileHandlerResponse
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorService
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
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
                .map(this::checkFileToUploadExists)
                .flatMap(this::waitForRemainingParts)
                .doOnSuccess(this::checkFileSize)
                .flatMap(this::writeToStorage)
                .flatMap(this::saveFileInfoToRepo)
                .map { it.t2 }
                .flatMap(this::sendResponse)
                .onErrorResume(this::handleErrors)
    }

    private fun checkFileToUploadExists(mvm: MultiValueMap<String, Part>): Pair<Part, String> {
        val part = mvm.getFirst(uploadingFilePartName)
                ?: throw NoFileToUploadException()

        //for test purpose
        val originalName = if (part is FilePart) {
            part.filename()
        } else {
            ""
        }

        return part to originalName
    }

    private fun waitForRemainingParts(it: Pair<Part, String>): Mono<Tuple2<MutableList<DataBuffer>, String>>? {
        val part = it.first
        val originalName = it.second

        val partsListMono = part.content()
                .buffer()
                .single()

        return Mono.zip(partsListMono, Mono.just(originalName))
    }

    private fun writeToStorage(it: Tuple2<MutableList<DataBuffer>, String>): Mono<FileInfo> {
        val partsList = it.t1
        val origName = it.t2
        val generatedName = generator.generateNewFileName()

        //if file does not have name (what?) then give it a name
        val originalName = if (origName.isNotEmpty()) {
            origName
        } else {
            generatedName
        }

        val extension = originalName.extractExtension()

        //if file does not have an extension then just don't use it
        val newFileName = if (extension.isEmpty()) {
            generatedName
        } else {
            "$generatedName.$extension"
        }

        val fullPath = Path(fileDirectoryPath, newFileName)

        //use "use" function so we don't forget to close the streams
        fs.create(fullPath).use { outputStream ->
            for (data in partsList) {
                data.asInputStream().use { inputStream ->
                    val chunkSize = inputStream.available()
                    val buffer = ByteArray(chunkSize)

                    //copy chunks from one stream to another
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
            throw MaxFilesSizeExceededException(fileSize, maxFileSize)
        }
    }

    private fun saveFileInfoToRepo(fileInfo: FileInfo): Mono<Tuple2<StoredFile, String>> {
        val repoResult = repo.save(StoredFile(fileInfo.newFileName,
                fileInfo.originalName, System.currentTimeMillis()))

        return Mono.zip(repoResult, Mono.just(fileInfo.newFileName))
    }

    private fun sendResponse(uploadedFileName: String): Mono<ServerResponse> {
        return ServerResponse.ok()
                .body(Mono.just(UploadFileHandlerResponse.success(uploadedFileName, ServerResponseCode.OK.value)))
    }

    private fun handleErrors(error: Throwable): Mono<ServerResponse> {
        return when (error) {
            is NoFileToUploadException -> ServerResponse.badRequest()
                    .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.NO_FILE_TO_UPLOAD.value, error.message!!)))
            is MaxFilesSizeExceededException -> ServerResponse.badRequest()
                    .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.MAX_FILE_SIZE_EXCEEDED.value, error.message!!)))

            is IOException -> {
                logger.error("Unhandled exception", error)
                val msg = error.message ?: "IOException while trying to store the file"
                ServerResponse.unprocessableEntity().
                        body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.UNKNOWN_ERROR.value, msg)))
            }

            else -> {
                logger.error("Unhandled exception", error)
                val msg = error.message ?: "Unknown error"
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.UNKNOWN_ERROR.value, msg)))
            }
        }
    }

    data class FileInfo(val newFileName: String,
                        val originalName: String)

    class NoFileToUploadException : Exception("The request does not contain \"file\" part")
    class MaxFilesSizeExceededException(fileSize: Long, maxfs: Long) : Exception("The size of the file " +
            "(${fileSize.toFloat() / (1024 * 1024)} MB) exceeds the limit (${maxfs.toFloat() / (1024 * 1024)} MB)")
}





