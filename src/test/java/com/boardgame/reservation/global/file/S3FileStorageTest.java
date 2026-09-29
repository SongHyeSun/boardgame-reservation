package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.support.ImageFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S3Client 를 모킹한 단위 테스트. 검증 규칙 자체는 LocalFileStorageTest 와 같은 ImageUploadValidator 를 쓴다. */
class S3FileStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final String VALID_KEY = "avatars/3f2b1c9e-0000-4000-8000-000000000001.png";

    S3Client s3Client;
    S3FileStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storage = new S3FileStorage(s3Client, BUCKET);
    }

    @Test
    @DisplayName("store: 검증 후 {dir}/{uuid}.{ext} key 로 버킷에 올리고 Content-Type 은 확장자에 맞는 표준 값")
    void store_putsObjectWithKeyAndContentType() {
        MockMultipartFile file = ImageFixtures.png("image");

        String key = storage.store(file, FileKeys.AVATARS);

        assertThat(FileKeys.isValid(key)).isTrue();
        assertThat(key).startsWith("avatars/").endsWith(".png");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(body.getValue().optionalContentLength()).hasValue(file.getSize());
    }

    @Test
    @DisplayName("store: 검증에 실패하면 R2 를 호출하지 않는다 (시그니처 불일치, 빈 파일)")
    void store_invalidFile_neverCallsS3() {
        MockMultipartFile mismatch = new MockMultipartFile("image", "a.png", "image/png", ImageFixtures.jpegBytes());
        MockMultipartFile empty = new MockMultipartFile("image", "a.png", "image/png", new byte[0]);

        for (MockMultipartFile file : new MockMultipartFile[]{mismatch, empty}) {
            assertThatThrownBy(() -> storage.store(file, FileKeys.AVATARS))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE);
        }
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("load: 잘못된 형식의 key 는 R2 를 호출하지 않고 FILE_NOT_FOUND")
    void load_invalidKey_notFoundWithoutCallingS3() {
        assertThatThrownBy(() -> storage.load("../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
        assertThatThrownBy(() -> storage.load(null))
                .isInstanceOf(BusinessException.class);
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("load: 있는 객체는 바이트 그대로 Resource 로 돌려준다")
    void load_existing_returnsBytes() throws IOException {
        byte[] bytes = ImageFixtures.pngBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));

        Resource resource = storage.load(VALID_KEY);

        assertThat(resource.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(resource.contentLength()).isEqualTo(bytes.length);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(VALID_KEY);
    }

    @Test
    @DisplayName("load: 객체가 없으면(NoSuchKey) FILE_NOT_FOUND")
    void load_missing_notFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("nope").build());

        assertThatThrownBy(() -> storage.load(VALID_KEY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("load: R2 장애(NoSuchKey 아님)는 404 로 위장하지 않고 서버 오류로 전파")
    void load_storageFailure_isNotNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatThrownBy(() -> storage.load(VALID_KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("delete: 멱등 — null·잘못된 key 는 아무것도 안 하고, R2 예외도 삼킨다")
    void delete_isIdempotentAndSwallowsErrors() {
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("bad key")).doesNotThrowAnyException();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(AwsServiceException.builder().message("boom").build());
        assertThatCode(() -> storage.delete(VALID_KEY)).doesNotThrowAnyException();
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
