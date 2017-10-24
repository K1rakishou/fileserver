package org.portfolio.fileserver.model.response

import org.codehaus.jackson.annotate.JsonProperty

class UploadFileHandlerResponse(@JsonProperty("uploaded_file_name")
                                val uploadedFileName: String?,

                                code: Int,
                                message: String? = null) : BaseServerResponse(code, message) {

    companion object {
        fun success(uploadedFileName: String, code: Int): UploadFileHandlerResponse {
            return UploadFileHandlerResponse(uploadedFileName, code)
        }

        fun fail(code: Int, message: String): UploadFileHandlerResponse {
            return UploadFileHandlerResponse(null, code, message)
        }
    }
}