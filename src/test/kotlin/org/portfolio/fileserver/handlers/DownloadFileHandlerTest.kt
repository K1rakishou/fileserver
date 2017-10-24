package org.portfolio.fileserver.handlers

import com.mongodb.ConnectionString
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.portfolio.fileserver.config.DB_SERVER_ADDRESS
import org.portfolio.fileserver.model.ServerResponseCode
import org.portfolio.fileserver.model.response.BaseServerResponse
import org.portfolio.fileserver.model.response.UploadFileHandlerResponse
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorServiceImpl
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

@RunWith(SpringRunner::class)
class DownloadFileHandlerTest {
    val fs = FileSystem.newInstance(Configuration())
    val template = ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(
            ConnectionString("mongodb://$DB_SERVER_ADDRESS/fileserver")))
    val repo = FilesRepository(template)
    val generator = GeneratorServiceImpl()

    private var fileDirectoryPath = Path(fs.homeDirectory, "files")

    private fun getWebTestClient(): WebTestClient {
        val downloadFileHandler = DownloadFileHandler(fs, repo)
        val uploadFileHandler = UploadFileHandler(fs, repo, generator)

        return WebTestClient.bindToRouterFunction(router {
            "/v1".nest {
                "/api".nest {
                    accept(MediaType.APPLICATION_JSON).nest {
                        GET("/download/{file_name}", downloadFileHandler::handleFileDownload)
                    }
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

    @Test
    fun `test download when everything is ok`() {
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

        val fileBody = webClient
                .get()
                .uri("v1/api/download/${response.uploadedFileName}")
                .exchange()
                .expectStatus().is2xxSuccessful
                .expectBody(String::class.java).returnResult().responseBody!!

        fs.delete(Path(fileDirectoryPath, response.uploadedFileName), false)
        repo.clear().block()

        assertEquals(true, fileBody.startsWith("Lorem ipsum dolor"))
    }

    @Test
    fun `test download when file name does not exist in the DB`() {
        val webClient = getWebTestClient()

        val result = webClient
                .get()
                .uri("v1/api/download/123.txt")
                .exchange()
                .expectStatus().isNotFound
                .expectBody(BaseServerResponse::class.java).returnResult().responseBody!!

        assertEquals(ServerResponseCode.FILE_NOT_FOUND.value, result.code)
        assertEquals("Could not find the file", result.message)
    }
}



























