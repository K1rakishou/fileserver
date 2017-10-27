package org.portfolio.fileserver.repository

import com.mongodb.client.result.DeleteResult
import org.portfolio.fileserver.model.StoredFile
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class FilesRepository(private val template: ReactiveMongoTemplate) {

    fun findById(id: String): Mono<StoredFile> {
        return template.findById(id, StoredFile::class.java)
    }

    fun findByMd5(md5: String): Mono<StoredFile> {
        return template.find(Query.query(Criteria.where("md5").`is`(md5)).limit(1), StoredFile::class.java)
                .switchIfEmpty(Flux.just(StoredFile.empty()))
                .single()
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