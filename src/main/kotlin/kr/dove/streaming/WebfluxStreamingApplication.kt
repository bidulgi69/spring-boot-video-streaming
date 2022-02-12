package kr.dove.streaming

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebfluxStreamingApplication

fun main(args: Array<String>) {
	runApplication<WebfluxStreamingApplication>(*args)
}
