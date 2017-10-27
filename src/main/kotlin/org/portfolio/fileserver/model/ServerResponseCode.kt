package org.portfolio.fileserver.model

enum class ServerResponseCode(val value: Int) {
    UNKNOWN_ERROR(-1),
    OK(0),
    NO_FILE_TO_UPLOAD(1),
    MAX_FILE_SIZE_EXCEEDED(2),
    FILE_NOT_FOUND(3),
    BAD_MEDIA_TYPE(4),
    NO_HEADERS(5),
    EMPTY_FILE(6)
}