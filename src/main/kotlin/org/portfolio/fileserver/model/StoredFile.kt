package org.portfolio.fileserver.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document


@Document(collection = "files")
data class StoredFile(@Id
                      val newFileName: String,
                      val oldFileName: String,
                      val uploadedOn: Long)