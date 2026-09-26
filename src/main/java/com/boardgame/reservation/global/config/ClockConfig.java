package com.boardgame.reservation.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 날짜·시간 판단은 Asia/Seoul 기준. 서비스는 Clock 을 주입받아 쓰고, 테스트에서는 고정 Clock 을 넣는다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
