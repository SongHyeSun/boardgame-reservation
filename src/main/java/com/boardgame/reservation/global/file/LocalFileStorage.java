package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 디스크 저장 구현. 저장 위치는 app.upload.dir (기본 ./uploads, .gitignore 대상).
 * app.storage.type=local(기본값)일 때만 등록된다 — 배포(prod)는 s3 (S3FileStorage).
 * 검증(5MB, 확장자+Content-Type+시그니처)은 ImageUploadValidator 가 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path baseDir;

    public LocalFileStorage(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String dir) {
        String key = ImageUploadValidator.validate(file, dir).key();
        Path target = resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to store file: " + key, e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        if (!FileKeys.isValid(key)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        if (!FileKeys.isValid(key)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("failed to delete file: {}", key, e);
        }
    }

    /** 정규식을 통과한 key 만 들어오지만, 방어적으로 base 하위인지 한 번 더 확인 */
    private Path resolve(String key) {
        Path path = baseDir.resolve(key).normalize();
        if (!path.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return path;
    }
}
