package org.portfolio.fileserver.model

import org.portfolio.fileserver.repository.FilesRepository
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document


@Document(collection = FilesRepository.COLLECTION_NAME)
data class StoredFile(@Id
                      val newFileName: String,
                      val oldFileName: String,
                      val uploadedOn: Long) {

    fun isEmpty(): Boolean {
        return newFileName.isEmpty() || oldFileName.isEmpty() || uploadedOn == 0L
    }

    companion object {
        fun empty(): StoredFile {
            return StoredFile("", "", 0L)
        }
    }
}