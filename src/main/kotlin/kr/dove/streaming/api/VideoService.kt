package kr.dove.streaming.api

import kr.dove.streaming.api.domain.Video
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer

interface VideoService {

    @GetMapping(
        value = ["/stream/{videoId}"],
        produces = ["video/mp4"]
    ) fun getStreamingBytes(
        @PathVariable(name = "videoId") videoId: String,
        @RequestHeader headers: HttpHeaders,
        exchange: ServerWebExchange): Mono<Resource>

    @PostMapping(
        value = ["/upload"],
        consumes = ["video/mp4"],
        produces = ["application/json"]
    ) fun upload(
        @RequestHeader headers: HttpHeaders,
        @RequestBody file: Flux<ByteBuffer>): Mono<Video>
}