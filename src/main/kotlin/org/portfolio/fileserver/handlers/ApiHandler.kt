package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

class ApiHandler(private val fs: FileSystem) {

    fun handleFileUpload(request: ServerRequest): Mono<ServerResponse> {
        return request.body(BodyExtractors.toMultipartData())
                .map { mvm -> mvm.getFirst("file") }
                .map { firstPart ->
                    if (firstPart == null) {
                        throw NullPointerException()
                    }

                    return@map firstPart
                }
                .flatMap { it!!.content().buffer().single() }
                .map { dataParts ->
                    fs.create(Path("D:\\123.jpg")).use { outputStream ->
                        var totalWritten = 0

                        for (data in dataParts) {
                            data.asInputStream().use { inputStream ->
                                val chunkSize = inputStream.available()

                                val buffer = ByteArray(chunkSize)
                                inputStream.read(buffer, 0, chunkSize)

                                outputStream.wrappedStream.write(buffer, 0, chunkSize)
                                totalWritten += chunkSize

                                System.err.println("Written total: $totalWritten, chuckSize: $chunkSize")
                            }
                        }
                    }
                }
                .flatMap { ServerResponse.ok().body(Mono.just("ok")) }
                .onErrorResume { error ->
                    if (error is NullPointerException) {
                        return@onErrorResume ServerResponse.unprocessableEntity().body(Mono.just("error"))
                    }

                    error.printStackTrace()
                    throw error
                }
    }
}