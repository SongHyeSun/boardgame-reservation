package com.boardgame.reservation.global.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/** app.storage.type=s3 일 때만 활성화: R2 용 S3Client + S3FileStorage. (local 이면 LocalFileStorage) */
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties) {
        return buildClient(properties);
    }

    @Bean
    public FileStorage s3FileStorage(S3Client s3Client, S3StorageProperties properties) {
        return new S3FileStorage(s3Client, properties.bucket());
    }

    /**
     * R2 용 클라이언트.
     * - region 은 R2 규약상 "auto", path-style 접근(https://endpoint/bucket/key).
     * - 체크섬은 WHEN_REQUIRED: SDK 2.30+ 기본값(WHEN_SUPPORTED)은 요청마다 CRC32 체크섬 헤더/트레일러를 붙이는데 R2 가 거부한다.
     * - HTTP 클라이언트는 경량 url-connection-client (512MB 서버라 apache/netty 는 classpath 에서 제외).
     */
    static S3Client buildClient(S3StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
