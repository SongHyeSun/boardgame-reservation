package com.boardgame.reservation.global.file;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 업로드 이미지 검증 + key 생성. FileStorage 구현체(Local, S3)가 공유한다 — 저장 위치가 달라도 검증 규칙은 하나.
 * 검증: 5MB 이하, 확장자 + Content-Type + 파일 시그니처(매직 바이트) 셋 다 확인하고 서로 일치해야 한다.
 */
final class ImageUploadValidator {

    static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 검증을 통과한 업로드. contentType 은 확장자에 대응하는 표준 값(클라이언트가 보낸 값이 아님) */
    record ValidatedUpload(String key, String contentType) {
    }

    private enum ImageType {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

        final String contentType;

        ImageType(String contentType) {
            this.contentType = contentType;
        }
    }

    private static final Map<String, ImageType> EXTENSIONS = Map.of(
            "jpg", ImageType.JPEG,
            "jpeg", ImageType.JPEG,
            "png", ImageType.PNG,
            "webp", ImageType.WEBP);

    private static final Map<String, ImageType> CONTENT_TYPES = Map.of(
            "image/jpeg", ImageType.JPEG,
            "image/png", ImageType.PNG,
            "image/webp", ImageType.WEBP);

    private ImageUploadValidator() {
    }

    /**
     * @param dir FileKeys.DIRS 중 하나
     * @throws BusinessException INVALID_FILE / FILE_TOO_LARGE
     */
    static ValidatedUpload validate(MultipartFile file, String dir) {
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

        return new ValidatedUpload(dir + "/" + UUID.randomUUID() + "." + ext, byExtension.contentType);
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
