package org.portfolio.fileserver.repository

import com.mongodb.client.result.DeleteResult
import org.portfolio.fileserver.model.StoredFile
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Mono

open class FilesRepository(private val template: ReactiveMongoTemplate) {

    fun findById(id: String): Mono<StoredFile> {
        return template.findById(id, StoredFile::class.java)
    }

    fun save(storedFile: StoredFile): Mono<StoredFile> {
        return template.save(storedFile)
    }

    fun clear(): Mono<DeleteResult> {
        return template.remove(Query(), COLLECTION_NAME)
    }

    companion object {
        const val COLLECTION_NAME = "files"
    }
}