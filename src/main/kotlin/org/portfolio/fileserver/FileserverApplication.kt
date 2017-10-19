package org.portfolio.fileserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FileserverApplication

fun main(args: Array<String>) {
    SpringApplication.run(FileserverApplication::class.java, *args)
}
