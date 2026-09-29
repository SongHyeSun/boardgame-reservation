package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Cloudflare R2(S3 호환) 저장 구현. app.storage.type=s3 (S3StorageConfig 가 등록).
 * key 형식·검증은 로컬 구현과 같다(FileKeys, ImageUploadValidator). 이미지는 5MB 이하라 통째로 읽어 ByteArrayResource 로 돌려준다
 * (FileController 가 Content-Length 를 알 수 있고, 응답 중에 R2 연결을 붙잡지 않는다).
 */
@Slf4j
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorage(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String store(MultipartFile file, String dir) {
        ImageUploadValidator.ValidatedUpload upload = ImageUploadValidator.validate(file, dir);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .contentType(upload.contentType())
                .build();
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read upload: " + upload.key(), e);
        } catch (SdkException e) {
            throw new IllegalStateException("failed to store file: " + upload.key(), e);
        }
        return upload.key();
    }

    @Override
    public Resource load(String key) {
        if (!FileKeys.isValid(key)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new ByteArrayResource(object.asByteArray());
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        } catch (SdkException e) {
            throw new IllegalStateException("failed to load file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        if (!FileKeys.isValid(key)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("failed to delete file: {}", key, e);
        }
    }
}
