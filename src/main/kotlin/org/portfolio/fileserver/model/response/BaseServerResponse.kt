package org.portfolio.fileserver.model.response

import com.fasterxml.jackson.annotation.JsonProperty

open class BaseServerResponse(@JsonProperty("code")
                              val code: Int,

                              @JsonProperty("message")
                              val message: String?)