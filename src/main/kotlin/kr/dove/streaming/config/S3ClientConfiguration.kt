package kr.dove.streaming.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "aws.credentials")
class S3ClientConfiguration(
    @Value("\${aws.s3.bucket.region}") var region: String,
) {
    lateinit var accessKeyId: String
    lateinit var secretAccessKey: String

    @Bean
    fun s3Client(awsCredentialProvider: AwsCredentialsProvider): S3AsyncClient {
        val httpClient = NettyNioAsyncHttpClient
            .builder()
            .writeTimeout(Duration.ZERO)
            .maxConcurrency(64)
            .build()

        val serviceConfiguration = S3Configuration
            .builder()
            .checksumValidationEnabled(false)
            .chunkedEncodingEnabled(true)
            .build()

        return S3AsyncClient
            .builder()
            .httpClient(httpClient)
            .region(Region.of(region))
            .credentialsProvider(awsCredentialProvider)
            .serviceConfiguration(serviceConfiguration)
            .build()
    }

    @Bean
    fun awsCredentialProvider(): AwsCredentialsProvider =
        AwsCredentialsProvider {
            AwsBasicCredentials.create(
                accessKeyId, secretAccessKey
            )
        }

}