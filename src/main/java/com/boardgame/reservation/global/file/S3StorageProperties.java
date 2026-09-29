package com.boardgame.reservation.global.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.storage.s3.* — Cloudflare R2 (S3 호환) 접속 정보. app.storage.type=s3 일 때만 사용한다.
 * 값은 환경 변수(R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY)로 주입하고 레포에 넣지 않는다.
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(String endpoint, String bucket, String accessKeyId, String secretAccessKey) {
}
