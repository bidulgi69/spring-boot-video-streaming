package kr.dove.streaming.service

import kr.dove.streaming.api.VideoService
import kr.dove.streaming.api.domain.Video
import kr.dove.streaming.exception.NoSuchObjectException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.SdkPublisher
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.min

@RestController
@RequestMapping(value = ["/video"])
class VideoServiceImpl(
    @Value("\${aws.s3.bucket.name}") private var bucket: String,
    private val s3Client: S3AsyncClient,
): VideoService {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val _chunk: Long = 1000000L

    override fun getStreamingBytes(
        videoId: String,
        headers: HttpHeaders,
        exchange: ServerWebExchange): Mono<Resource> {
        logger.info("Request Video Id: {}", videoId)
        val future = s3Client
            .getObject(GetObjectRequest
                .builder()
                .bucket(bucket)
                .key(videoId)
                .build(),
                S3ResponseProvider(S3Response(flux = Flux.empty()))
            )
        return Mono.fromFuture(future)
            .flatMap { response ->
                val contentLength: Long = response.sdkResponse!!.contentLength()
                //  To return a publisher,
                //  * If you want to return a ResponseEntity<?>, you should find another way to return a publisher(Flux).
                //  call ServerWebExchange.response to handle the headers of message.
                //  HttpStatus, Content-Type, Content-Length are the essentials to be mutated.
                exchange.response.statusCode = HttpStatus.PARTIAL_CONTENT
                exchange.response.headers.set(HttpHeaders.CONTENT_TYPE, response.sdkResponse!!.contentType())
                exchange.response.headers.set(HttpHeaders.CONTENT_LENGTH, "$contentLength")
                response.flux
                    .collectList()
                    .flatMap { byteBuffers ->
                        //  combines ByteBuffers and gathers them into ByteArray.
                        val fullContent = ByteBuffer.allocate(
                            byteBuffers.stream()
                                .mapToInt(ByteBuffer::capacity)
                                .sum()
                        )
                        byteBuffers.forEach(fullContent::put)
                        fullContent.flip()
                        //  Return Resource
                        Mono.just(ByteArrayResource(fullContent.array()))
                    }.flatMap { resource ->
                        //  Using Http Range Requests
                        val start: Long = headers.range.getOrNull(0) ?.getRangeStart(contentLength) ?: 0L
                        val end: Long = headers.range.getOrNull(0) ?.getRangeEnd(contentLength) ?: min(_chunk, contentLength)
                        //  ResourceRegion helps you to split Resource object.
                        Mono.just(
                            ResourceRegion(resource, start, min(_chunk, end - start + 1))
                                .resource   //  it will return the split resources.
                        )
                    }
            }
            .onErrorResume {
                Mono.error(NoSuchObjectException("Requested an invalid video id."))
            }
    }

    override fun upload(headers: HttpHeaders, file: Flux<ByteBuffer>): Mono<Video> {
        logger.info("Content-Type: {}", headers.contentType)
        val key: String = headers.getFirst("originalFilename") ?: UUID.randomUUID().toString()
        val future: CompletableFuture<PutObjectResponse> = s3Client
            .putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(headers.contentType ?. toString() ?: MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .contentLength(headers.contentLength)
                .build(),
                AsyncRequestBody.fromPublisher(file)
            )
        return Mono.fromFuture(future)
            .flatMap { response ->
                logger.info("Response from put object request $response")
                Mono.just(Video(
                    key
                ))
            }
    }
}

open class S3ResponseProvider(
    private var response: S3Response
): AsyncResponseTransformer<GetObjectResponse, S3Response> {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun prepare(): CompletableFuture<S3Response> {
        logger.info("Prepare connection...")
        return response.future
    }

    override fun onResponse(response: GetObjectResponse) {
        logger.info("Get response from bucket. {}", response.eTag())
        this.response.sdkResponse = response
    }

    override fun onStream(publisher: SdkPublisher<ByteBuffer>) {
        this.response.flux = publisher.toFlux()
        this.response.future.complete(response)
    }

    override fun exceptionOccurred(error: Throwable) {
        response.future.completeExceptionally(error)
    }

}

data class S3Response(
    var future: CompletableFuture<S3Response> = CompletableFuture(),
    var sdkResponse: GetObjectResponse? = null,
    var flux: Flux<ByteBuffer>,
)