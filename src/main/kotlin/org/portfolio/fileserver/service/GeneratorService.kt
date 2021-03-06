package org.portfolio.fileserver.service

interface GeneratorService {
    fun generateRandomString(len: Int, alphabet: String): String
    fun generateNewFileName(): String
}