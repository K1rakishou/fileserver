package org.portfolio.fileserver.handlers

import com.mongodb.ConnectionString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.portfolio.fileserver.config.DB_SERVER_ADDRESS
import org.portfolio.fileserver.model.ServerResponseCode
import org.portfolio.fileserver.model.response.UploadFileHandlerResponse
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorServiceImpl
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.router
import reactor.test.StepVerifier
import java.io.File

@RunWith(SpringRunner::class)
class UploadFileHandlerTest {
    val template = ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(
            ConnectionString("mongodb://$DB_SERVER_ADDRESS/fileserver")))
    val repo = FilesRepository(template)
    val generator = GeneratorServiceImpl()

    private var fileDirectoryPath = "D:\\files"

    private fun getWebTestClient(): WebTestClient {
        val uploadFileHandler = UploadFileHandler(repo, generator)

        return WebTestClient.bindToRouterFunction(router {
            "/v1".nest {
                "/api".nest {
                    accept(MediaType.MULTIPART_FORM_DATA).nest {
                        POST("/upload", uploadFileHandler::handleFileUpload)
                    }
                }
            }
        }).build()
    }

    fun createMultipartFile(): MultiValueMap<String, Any> {
        val fileResource = ClassPathResource("test_file.txt")
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.TEXT_PLAIN
        val part = HttpEntity(fileResource, fileHeaders)
        val parts = LinkedMultiValueMap<String, Any>()
        parts.add("file", part)

        return parts
    }

    fun createVeryBigMultipartFile(): MultiValueMap<String, Any> {
        val bytes = ByteArray(10242880)
        for (i in 0 until bytes.size) {
            bytes[i] = 55
        }

        val resource = ByteArrayResource(bytes)

        val parts = LinkedMultiValueMap<String, Any>()
        parts.add("file", resource)

        return parts
    }

    fun createEmptyMultipartFile(): MultiValueMap<String, Any> {
        return LinkedMultiValueMap<String, Any>()
    }

    @Test
    fun `test upload when everything is ok`() {
        val webClient = getWebTestClient()
        val file = createMultipartFile()

        val response = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().is2xxSuccessful
                .expectBody(UploadFileHandlerResponse::class.java).returnResult().responseBody!!

        assertEquals(ServerResponseCode.OK.value, response.code)
        val uploadedFileName = response.uploadedFileName!!

        StepVerifier.create(repo.findById(uploadedFileName))
                .assertNext { storedFile ->
                    assertEquals(storedFile.newFileName, uploadedFileName)
                }
                .verifyComplete()

        val path = "$fileDirectoryPath\\${response.uploadedFileName}"

        File(path).delete()
        repo.clear().block()
    }

    @Test
    fun `test upload when no file was sent to upload`() {
        val webClient = getWebTestClient()
        val file = createEmptyMultipartFile()

        val response = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(UploadFileHandlerResponse::class.java).returnResult().responseBody!!

        assertEquals(ServerResponseCode.NO_FILE_TO_UPLOAD.value, response.code)
        assertEquals("The request does not contain \"file\" part", response.message)
    }

    @Test
    fun `test upload when file size is exceeding the limit`() {
        val webClient = getWebTestClient()
        val file = createVeryBigMultipartFile()

        val response = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(UploadFileHandlerResponse::class.java).returnResult().responseBody!!

        assertEquals(ServerResponseCode.MAX_FILE_SIZE_EXCEEDED.value, response.code)
        assertEquals(true, response.message!!.startsWith("The size of the file"))
    }
}


















