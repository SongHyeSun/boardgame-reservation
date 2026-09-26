package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.support.ImageFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 임시 디렉터리(@TempDir)에만 쓴다. Spring 컨텍스트 없이 검증. */
class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(tempDir.toString());
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ───────────── store: 정상 ─────────────

    @Test
    @DisplayName("jpg/png/webp 는 {dir}/{uuid}.{ext} key 로 저장되고 파일이 생긴다")
    void store_validImages() throws IOException {
        for (MockMultipartFile file : new MockMultipartFile[]{
                ImageFixtures.jpeg("image"), ImageFixtures.png("image"), ImageFixtures.webp("image")}) {
            String key = storage.store(file, FileKeys.AVATARS);

            assertThat(FileKeys.isValid(key)).isTrue();
            assertThat(key).startsWith("avatars/");
            assertThat(tempDir.resolve(key)).isRegularFile();
            assertThat(tempDir.resolve(key)).hasBinaryContent(file.getBytes());
        }
    }

    @Test
    @DisplayName("jpeg 확장자, 대문자 확장자(.JPG)도 허용되고 key 확장자는 소문자")
    void store_jpegAndUppercaseExtension() {
        String jpeg = storage.store(
                new MockMultipartFile("image", "a.jpeg", "image/jpeg", ImageFixtures.jpegBytes()), FileKeys.BOARDGAMES);
        String upper = storage.store(
                new MockMultipartFile("image", "A.JPG", "IMAGE/JPEG", ImageFixtures.jpegBytes()), FileKeys.AVATARS);

        assertThat(jpeg).startsWith("boardgames/").endsWith(".jpeg");
        assertThat(upper).startsWith("avatars/").endsWith(".jpg");
    }

    @Test
    @DisplayName("정확히 5MB 는 허용, 5MB + 1바이트는 FILE_TOO_LARGE")
    void store_sizeLimit() {
        byte[] exactly = Arrays.copyOf(ImageFixtures.jpegBytes(), 5 * 1024 * 1024);
        byte[] over = Arrays.copyOf(ImageFixtures.jpegBytes(), 5 * 1024 * 1024 + 1);

        assertThat(FileKeys.isValid(storage.store(
                new MockMultipartFile("image", "a.jpg", "image/jpeg", exactly), FileKeys.AVATARS))).isTrue();
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.jpg", "image/jpeg", over), FileKeys.AVATARS), ErrorCode.FILE_TOO_LARGE);
    }

    // ───────────── store: 거부 ─────────────

    @Test
    @DisplayName("시그니처가 확장자와 다르면(png 내용을 .jpg 로) INVALID_FILE")
    void store_signatureMismatch() {
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.jpg", "image/jpeg", ImageFixtures.pngBytes()), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
    }

    @Test
    @DisplayName("확장자가 시그니처·Content-Type 과 다르면 INVALID_FILE")
    void store_extensionMismatch() {
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.png", "image/jpeg", ImageFixtures.jpegBytes()), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
    }

    @Test
    @DisplayName("Content-Type 이 다르거나 없으면 INVALID_FILE")
    void store_contentTypeMismatch() {
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.jpg", "image/png", ImageFixtures.jpegBytes()), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.jpg", null, ImageFixtures.jpegBytes()), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.gif", "a.svg", "a.txt", "a", "a.jpg.exe"})
    @NullSource
    @DisplayName("허용되지 않는 확장자·확장자 없음·파일명 없음은 INVALID_FILE")
    void store_disallowedExtension(String filename) {
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", filename, "image/jpeg", ImageFixtures.jpegBytes()), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
    }

    @Test
    @DisplayName("빈 파일은 INVALID_FILE")
    void store_empty() {
        assertErrorCode(() -> storage.store(
                new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]), FileKeys.AVATARS),
                ErrorCode.INVALID_FILE);
    }

    @Test
    @DisplayName("허용되지 않은 dir 은 프로그래밍 오류(IllegalArgumentException)")
    void store_unsupportedDir() {
        assertThatThrownBy(() -> storage.store(ImageFixtures.jpeg("image"), "../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────── load ─────────────

    @Test
    @DisplayName("저장한 파일은 key 로 읽을 수 있다")
    void load_stored() throws IOException {
        String key = storage.store(ImageFixtures.png("image"), FileKeys.AVATARS);

        Resource resource = storage.load(key);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getContentAsByteArray()).isEqualTo(ImageFixtures.pngBytes());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret.png",
            "avatars/../../secret.png",
            "avatars/../avatars/00000000-0000-0000-0000-000000000000.png",
            "/etc/passwd",
            "avatars/not-a-uuid.png",
            "avatars/00000000-0000-0000-0000-000000000000.gif",
            "other/00000000-0000-0000-0000-000000000000.png",
            "avatars/00000000-0000-0000-0000-000000000000.png/",
            ""})
    @NullSource
    @DisplayName("형식이 잘못된 key(경로 조작 포함)는 FILE_NOT_FOUND")
    void load_invalidKey(String key) {
        assertErrorCode(() -> storage.load(key), ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("형식은 맞지만 없는 파일은 FILE_NOT_FOUND")
    void load_missing() {
        assertErrorCode(() -> storage.load("avatars/00000000-0000-0000-0000-000000000000.png"),
                ErrorCode.FILE_NOT_FOUND);
    }

    // ───────────── delete ─────────────

    @Test
    @DisplayName("delete 는 파일을 지우고, 다시 호출해도 예외가 없다(멱등)")
    void delete_idempotent() {
        String key = storage.store(ImageFixtures.jpeg("image"), FileKeys.AVATARS);

        storage.delete(key);
        assertThat(tempDir.resolve(key)).doesNotExist();

        storage.delete(key);
    }

    @Test
    @DisplayName("delete 는 null·잘못된 key 를 조용히 무시하고, base 밖 파일은 건드리지 않는다")
    void delete_ignoresInvalidKey() throws IOException {
        Path outside = Files.createTempFile("outside", ".png");
        try {
            storage.delete(null);
            storage.delete("");
            storage.delete("../" + outside.getFileName());
            storage.delete(outside.toString());

            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
