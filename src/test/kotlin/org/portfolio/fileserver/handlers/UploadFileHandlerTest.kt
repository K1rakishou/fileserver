package org.portfolio.fileserver.handlers

import org.apache.hadoop.fs.FileSystem
import org.mockito.Mock
import org.portfolio.fileserver.repository.FilesRepository
import org.portfolio.fileserver.service.GeneratorService
import org.springframework.beans.factory.annotation.Autowired

class UploadFileHandlerTest {

    @Autowired
    lateinit var uploadFileHandler: UploadFileHandler

    @Mock
    lateinit var fs: FileSystem

    @Mock
    lateinit var repo: FilesRepository

    @Mock
    lateinit var generator: GeneratorService

    /*@Test
    fun `sample test`() {
        val request = MockServerRequest.builder().build()
        val fileResource = ClassPathResource("src/test/resources/test_file.txt")
        val part = MockPart("file", "123.jpg", fileResource.inputStream.readBytes())
        val mvm = LinkedMultiValueMap<String, Part>()
        mvm.add("file", part as Part)

        Mockito.`when`(request.body(BodyExtractors.toMultipartData())).thenReturn(Mono.just(mvm))

        StepVerifier.create(uploadFileHandler.handleFileUpload(request))
                .expectNext()
    }*/

}


















