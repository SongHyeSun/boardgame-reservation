package com.boardgame.reservation.global.file;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * GET /api/files/{dir}/{filename} — 업로드 이미지 서빙 (SecurityConfig 에서 permitAll).
 * key 형식 검증은 FileStorage.load 가 한다: 형식이 다르거나 파일이 없으면 FILE_NOT_FOUND(404).
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private final FileStorage fileStorage;

    @GetMapping("/{dir}/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String dir, @PathVariable String filename) {
        String key = dir + "/" + filename;
        Resource resource = fileStorage.load(key);
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(key))
                // key 에 uuid 가 들어가 내용이 바뀌지 않는다 → 장기 캐시
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(resource);
    }

    private static MediaType mediaTypeOf(String key) {
        return switch (FileKeys.extensionOf(key)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> IMAGE_WEBP;
            default -> MediaType.IMAGE_JPEG;
        };
    }
}
