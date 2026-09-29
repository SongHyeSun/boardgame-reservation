package com.boardgame.reservation.global.health;

import com.boardgame.reservation.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health — 배포 플랫폼(Render Health Check) 용 생존 확인. SecurityConfig 에서 permitAll.
 * DB·Redis 를 일부러 건드리지 않는다: 헬스 체크가 주기적으로 Neon 을 깨우거나 Upstash 명령을 소모하면 안 되고,
 * 외부 저장소가 잠깐 느려도 인스턴스가 죽은 것으로 판정돼 재시작되지 않게 하기 위함.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Void>> health() {
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
