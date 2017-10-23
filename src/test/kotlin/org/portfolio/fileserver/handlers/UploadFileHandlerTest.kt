package org.portfolio.fileserver.handlers

import com.mongodb.ConnectionString
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.junit.Test
import org.junit.runner.RunWith
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

@RunWith(SpringRunner::class)
class UploadFileHandlerTest {

    val fs = FileSystem.newInstance(Configuration())
    val template = ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(
            ConnectionString("mongodb://192.168.99.100:27017/fileserver")))
    val repo = FilesRepository(template)
    val generator = GeneratorServiceImpl()

    private var fileDirectoryPath = Path(fs.homeDirectory, "files")

    private fun getWebTestClient(): WebTestClient {
        val uploadFileHandler = UploadFileHandler(fs, repo, generator)

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
    fun `test when everything is ok`() {
        val webClient = getWebTestClient()
        val file = createMultipartFile()

        val result = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().is2xxSuccessful
                .expectBody(String::class.java).returnResult().responseBody!!

        StepVerifier.create(repo.findById(result))
                .assertNext { storedFile ->
                    assert(result == storedFile.newFileName)
                }
                .verifyComplete()

        fs.delete(Path(fileDirectoryPath, result), false)
        repo.clear().block()
    }

    @Test
    fun `test when no file was sent to upload`() {
        val webClient = getWebTestClient()
        val file = createEmptyMultipartFile()

        val errorMessage = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java).returnResult().responseBody!!

        assert("The request does not contain \"file\" part" == errorMessage)
    }

    @Test
    fun `test when file size is exceeding the limit`() {
        val webClient = getWebTestClient()
        val file = createVeryBigMultipartFile()

        val errorMessage = webClient
                .post()
                .uri("v1/api/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java).returnResult().responseBody!!

        println(errorMessage)
    }
}


















