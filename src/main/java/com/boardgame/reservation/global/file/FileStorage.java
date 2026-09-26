package com.boardgame.reservation.global.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 저장소 추상화. 지금은 LocalFileStorage, 7단계 배포 때 S3 구현체로 교체 예정.
 * → 이 인터페이스 밖에서는 로컬 경로를 가정하지 말고, 항상 key(FileKeys 형식)로만 다룬다.
 */
public interface FileStorage {

    /**
     * 검증(5MB, jpg/jpeg/png/webp, 확장자+Content-Type+시그니처)을 통과한 파일을 저장하고 key 를 돌려준다.
     *
     * @param dir FileKeys.DIRS 중 하나 (avatars / boardgames)
     * @throws com.boardgame.reservation.global.exception.BusinessException INVALID_FILE / FILE_TOO_LARGE
     */
    String store(MultipartFile file, String dir);

    /** @throws com.boardgame.reservation.global.exception.BusinessException 형식이 잘못된 key 이거나 파일이 없으면 FILE_NOT_FOUND */
    Resource load(String key);

    /** 멱등: key 가 null·잘못된 형식·이미 없는 파일이어도 예외 없이 끝난다 (정리 작업에서 호출되므로 실패해도 흐름을 깨지 않음) */
    void delete(String key);
}
