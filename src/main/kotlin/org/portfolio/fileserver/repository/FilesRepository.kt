package org.portfolio.fileserver.repository

import org.portfolio.fileserver.model.StoredFile
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono

class FilesRepository(private val template: ReactiveMongoTemplate) {

    fun findById(id: String): Mono<StoredFile> {
        return template.findById(id, StoredFile::class.java)
    }

    fun save(storedFile: StoredFile): Mono<StoredFile> {
        return template.save(storedFile)
    }
}