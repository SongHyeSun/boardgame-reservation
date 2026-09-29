package com.boardgame.reservation.global.file;

import com.boardgame.reservation.support.ImageFixtures;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 호환 클라이언트 설정을 실제 HTTP 요청으로 검증한다 (로컬 가짜 서버가 요청을 받아 헤더를 기록).
 * - path-style: /{bucket}/{key}
 * - 체크섬 WHEN_REQUIRED: R2 가 거부하는 x-amz-checksum-* / x-amz-sdk-checksum-algorithm 헤더가 붙지 않는다
 *   (SDK 기본값 WHEN_SUPPORTED 였다면 PutObject 에 CRC32 체크섬이 붙는다).
 */
class S3StorageConfigTest {

    HttpServer server;
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> method = new AtomicReference<>();
    final Map<String, String> headers = new TreeMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("ETag", "\"etag\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("PutObject 는 path-style 로 나가고 체크섬 헤더가 붙지 않는다")
    void putObject_pathStyle_withoutChecksumHeaders() {
        S3StorageProperties properties = new S3StorageProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "my-bucket", "AKIATEST", "secret");
        try (S3Client client = S3StorageConfig.buildClient(properties)) {
            S3FileStorage storage = new S3FileStorage(client, properties.bucket());

            String key = storage.store(
                    new MockMultipartFile("image", "a.png", "image/png", ImageFixtures.pngBytes()), FileKeys.AVATARS);

            assertThat(method.get()).isEqualTo("PUT");
            assertThat(path.get()).isEqualTo("/my-bucket/" + key);
            assertThat(headers.get("authorization")).contains("AKIATEST");
            assertThat(headers.keySet())
                    .noneMatch(name -> name.startsWith("x-amz-checksum-"))
                    .doesNotContain("x-amz-sdk-checksum-algorithm", "x-amz-trailer");
        }
    }
}
