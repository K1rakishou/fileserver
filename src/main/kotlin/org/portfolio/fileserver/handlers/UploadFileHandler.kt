package org.portfolio.fileserver.handlers

import org.portfolio.fileserver.extensions.extractExtension
import org.portfolio.fileserver.extensions.toHex
import org.portfolio.fileserver.model.ServerResponseCode
import org.portfolio.fileserver.model.StoredFile
import org.portfolio.fileserver.model.response.UploadFileHandlerResponse
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorService
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import reactor.util.function.Tuple3
import java.io.File
import java.io.IOException
import java.security.MessageDigest


class UploadFileHandler(private val repo: FilesRepository,
                        private val generator: GeneratorService) {

    private val maxFileSize = 5242880L //5MB
    private val logger = LoggerFactory.getLogger(UploadFileHandler::class.java)
    private var fileDirectoryPath = "D:\\files"
    private val uploadingFilePartName = "file"

    fun handleFileUpload(request: ServerRequest): Mono<ServerResponse> {
        println("New request from ${request.headers().host()}")

        if (!request.headers().contentType().isPresent) {
            return ServerResponse.badRequest()
                    .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.NO_HEADERS.value,
                            "Request must have headers")))
        }

        val isMultipartFormData = request.headers().contentType().get().includes(MediaType.MULTIPART_FORM_DATA)
        if (!isMultipartFormData) {
            return ServerResponse.badRequest()
                    .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.BAD_MEDIA_TYPE.value,
                            "Request must have mediaType ${MediaType.MULTIPART_FORM_DATA_VALUE}")))
        }

        val allFilePartsMono = request.body(BodyExtractors.toMultipartData())
                .map(this::checkFileToUploadExists)
                .flatMap(this::waitForRemainingParts)
                .doOnSuccess(this::checkFileSize)
                .flux()
                .share()

        //calculate file md5
        val fileMd5Mono = allFilePartsMono
                .map(this::calcMd5)
                .share()

        //try to find file in the DB by md5
        val fileInDbMono = fileMd5Mono
                .flatMap { repo.findByMd5(it) }
                .publish()
                .autoConnect(2)

        //if found - just return it's name
        val fileFoundInDbMono = fileInDbMono
                .filter { !it.isEmpty() }
                .map {
                    return@map it.newFileName
                }

        //if not found - write file to the disk, and save file info to the DB
        val fileNotFoundInDbMono = Flux.zip(fileInDbMono, allFilePartsMono, fileMd5Mono)
                .filter { it.t1.isEmpty() }
                .flatMap(this::writeFileToDisk)
                .flatMap(this::saveFileInfoToRepo)
                .map { it.t2 }

        return Flux.merge(fileFoundInDbMono, fileNotFoundInDbMono)
                .single()
                .flatMap(this::sendResponse)
                .onErrorResume(this::handleErrors)
    }

    private fun writeFileToDisk(it: Tuple3<StoredFile, Tuple2<MutableList<DataBuffer>, String>, String>): Mono<FileInfo>? {
        val partsList = it.t2.t1
        val originalFileName = it.t2.t2
        val fileMd5 = it.t3

        val generatedName = generator.generateNewFileName()

        //if the file does not have a name (what?) then give it a name
        val originalName = if (originalFileName.isNotEmpty()) {
            originalFileName
        } else {
            generatedName
        }

        val extension = originalName.extractExtension()

        //if the file does not have an extension then just don't use it
        val newFileName = if (extension.isEmpty()) {
            generatedName
        } else {
            "$generatedName.$extension"
        }

        val fullPath = "$fileDirectoryPath\\$newFileName"
        val outFile = File(fullPath)

        //use "use" function so we don't forget to close the streams
        outFile.outputStream().use { outputStream ->
            for (data in partsList) {
                data.asInputStream().use { inputStream ->
                    val chunkSize = inputStream.available()
                    val buffer = ByteArray(chunkSize)

                    //copy chunks from one stream to another
                    inputStream.read(buffer, 0, chunkSize)
                    outputStream.write(buffer, 0, chunkSize)
                }
            }
        }

        return Mono.just(FileInfo(newFileName, originalName, fileMd5))
    }

    private fun calcMd5(it: Tuple2<MutableList<DataBuffer>, String>): String {
        val partsList = it.t1
        val md5Instance = MessageDigest.getInstance("MD5")

        for (part in partsList) {
            val byteArray = part.asByteBuffer().array()
            md5Instance.update(byteArray, 0, byteArray.size)
        }

        return md5Instance.digest().toHex()
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

    private fun waitForRemainingParts(it: Pair<Part, String>): Mono<Tuple2<MutableList<DataBuffer>, String>> {
        val part = it.first
        val originalName = it.second

        val partsListMono = part.content()
                .doOnNext {
                    if (it.readableByteCount() == 0) {
                        throw EmptyFileException()
                    }
                }
                .buffer()
                .single()

        return Mono.zip(partsListMono, Mono.just(originalName))
    }

    private fun checkFileSize(it: Tuple2<MutableList<DataBuffer>, String>) {
        val dataBufferList = it.t1
        val fileSize: Long = dataBufferList
                .map { it.readableByteCount().toLong() }
                .sum()

        if (fileSize > maxFileSize) {
            throw MaxFilesSizeExceededException(fileSize, maxFileSize)
        } else if (fileSize == 0L) {
            throw EmptyFileException()
        }
    }

    private fun saveFileInfoToRepo(fileInfo: FileInfo): Mono<Tuple2<StoredFile, String>> {
        val repoResult = repo.save(StoredFile(fileInfo.newFileName,
                fileInfo.originalName, System.currentTimeMillis(), fileInfo.fileMd5))

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
            is EmptyFileException -> ServerResponse.badRequest()
                    .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.EMPTY_FILE.value, error.message!!)))

            is IOException -> {
                logger.error("Unhandled exception", error)
                val msg = error.message ?: "IOException while trying to store the file"
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Mono.just(UploadFileHandlerResponse.fail(ServerResponseCode.UNKNOWN_ERROR.value, msg)))
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
                        val originalName: String,
                        val fileMd5: String)

    class EmptyFileException : Exception("The file is empty")
    class NoFileToUploadException : Exception("The request does not contain \"file\" part")
    class MaxFilesSizeExceededException(fileSize: Long, maxSize: Long) : Exception("The size of the file " +
            "(${fileSize.toFloat() / (1024 * 1024)} MB) exceeds the limit (${maxSize.toFloat() / (1024 * 1024)} MB)")
}
























