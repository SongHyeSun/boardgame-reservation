package com.boardgame.reservation.support;

import org.springframework.mock.web.MockMultipartFile;

/** 파일 시그니처(매직 바이트)만 맞춘 최소 이미지 바이트. 실제로 디코딩되는 이미지는 아니다. */
public final class ImageFixtures {

    private ImageFixtures() {
    }

    public static byte[] jpegBytes() {
        return bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01);
    }

    public static byte[] pngBytes() {
        return bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D);
    }

    public static byte[] webpBytes() {
        return bytes('R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ');
    }

    /** 시그니처·확장자·Content-Type 이 모두 일치하는 정상 파일 */
    public static MockMultipartFile jpeg(String partName) {
        return new MockMultipartFile(partName, "photo.jpg", "image/jpeg", jpegBytes());
    }

    public static MockMultipartFile png(String partName) {
        return new MockMultipartFile(partName, "photo.png", "image/png", pngBytes());
    }

    public static MockMultipartFile webp(String partName) {
        return new MockMultipartFile(partName, "photo.webp", "image/webp", webpBytes());
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
