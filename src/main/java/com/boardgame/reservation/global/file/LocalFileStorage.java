package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 로컬 디스크 저장 구현. 저장 위치는 app.upload.dir (기본 ./uploads, .gitignore 대상).
 * 검증: 5MB 이하, 확장자 + Content-Type + 파일 시그니처(매직 바이트) 셋 다 확인하고 서로 일치해야 한다.
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    static final long MAX_SIZE = 5L * 1024 * 1024;

    private enum ImageType { JPEG, PNG, WEBP }

    private static final Map<String, ImageType> EXTENSIONS = Map.of(
            "jpg", ImageType.JPEG,
            "jpeg", ImageType.JPEG,
            "png", ImageType.PNG,
            "webp", ImageType.WEBP);

    private static final Map<String, ImageType> CONTENT_TYPES = Map.of(
            "image/jpeg", ImageType.JPEG,
            "image/png", ImageType.PNG,
            "image/webp", ImageType.WEBP);

    private final Path baseDir;

    public LocalFileStorage(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String dir) {
        if (!FileKeys.DIRS.contains(dir)) {
            throw new IllegalArgumentException("unsupported upload dir: " + dir);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String ext = extensionOf(file.getOriginalFilename());
        ImageType byExtension = ext == null ? null : EXTENSIONS.get(ext);
        ImageType byContentType = CONTENT_TYPES.get(normalizeContentType(file.getContentType()));
        ImageType bySignature = detectBySignature(file);
        if (byExtension == null || byExtension != byContentType || byExtension != bySignature) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }

        String key = dir + "/" + UUID.randomUUID() + "." + ext;
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

    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "image/jpeg; charset=..." 같은 파라미터는 제거하고 소문자로 */
    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static ImageType detectBySignature(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read file signature", e);
        }
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return ImageType.PNG;
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }
}
