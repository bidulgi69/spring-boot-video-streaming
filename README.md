# spring-boot-video-streaming
Video streaming server using Spring Boot, Webflux, AWS S3

# Test
## Upload
    curl -H "Content-Type: video/mp4" -H "originalFilename: xxxxx.mp4" --data-binary "@xxxxx.mp4" "localhost:8080/video/upload" -XPOST
    {"videoId":"fav.mp4"}%
    
## Streaming (Download)
    I recommend you to open a web browser and access it with the url("http://localhost:8080/video/stream/xxxxx.mp4")

## Versions
Spring Boot, Webflux 2.6.3
S3-sdk 2.17.128
