package com.boardgame.reservation.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.session.autoconfigure.SessionAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisProperties;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * application-prod.yml 의 spring.session.data.redis.cleanup-cron 이 "실제로" 적용되는지 확인한다.
 * 속성 이름이 틀리면 Spring Boot 는 에러 없이 무시하고 기본값(매분)으로 돌기 때문에, 문자열 비교가 아니라
 *  ① Boot 의 SessionDataRedisProperties 로 바인딩되는지(이름이 유효한지)
 *  ② 실제 세션 저장소 빈(RedisIndexedSessionRepository)의 cleanupCron 에 반영되는지(자동설정이 넘겨주는지)
 * 를 확인한다. 대조군으로, prod 값을 빼면 기본값(매분)으로 돌아가는 것도 확인해 이 테스트가 값을 구분함을 보증한다.
 * Redis 는 필요 없다 (연결 팩토리는 모킹, configure-action=none 으로 기동 시 CONFIG 호출도 생략).
 */
class ProdSessionCleanupCronTest {

    private static final String PROD_CRON = "0 */10 * * * *";
    private static final String BOOT_DEFAULT_CRON = "0 * * * * *";

    private WebApplicationContextRunner runner(boolean withProdYml) {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SessionAutoConfiguration.class, SessionDataRedisAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                // 세션 만료 이벤트 리스너 컨테이너는 기동 시 Redis 에 구독을 건다 → 자동 시작만 끈다 (설정 값 검증에는 불필요)
                .withBean("noAutoStartListenerContainer", BeanPostProcessor.class, () -> new BeanPostProcessor() {
                    @Override
                    public Object postProcessBeforeInitialization(Object bean, String beanName) {
                        if (bean instanceof RedisMessageListenerContainer container) {
                            container.setAutoStartup(false);
                        }
                        return bean;
                    }
                })
                .withPropertyValues(
                        "spring.session.data.redis.repository-type=indexed",   // main application.yml 과 동일
                        "spring.session.data.redis.configure-action=none");    // 테스트 전용: 모킹한 연결로 CONFIG 를 호출하지 않는다
        if (withProdYml) {
            runner = runner.withInitializer(context ->
                    context.getEnvironment().getPropertySources().addLast(prodYaml()));
        }
        return runner;
    }

    private static PropertySource<?> prodYaml() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application-prod", new ClassPathResource("application-prod.yml"));
            assertThat(sources).as("application-prod.yml 을 읽지 못함").isNotEmpty();
            return sources.get(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @param boundProperty Boot 프로퍼티에 바인딩된 값(설정 안 했으면 null), @param effective 저장소가 실제로 쓰는 값 */
    private static void assertCleanupCron(AssertableWebApplicationContext context, String boundProperty, String effective) {
        assertThat(context).hasNotFailed();
        assertThat(context.getBean(SessionDataRedisProperties.class).getCleanupCron())
                .as("Boot 프로퍼티 바인딩").isEqualTo(boundProperty);
        assertThat(ReflectionTestUtils.getField(context.getBean(RedisIndexedSessionRepository.class), "cleanupCron"))
                .as("세션 저장소에 실제로 적용된 값").isEqualTo(effective);
    }

    @Test
    @DisplayName("prod 프로필의 cleanup-cron(10분마다)이 세션 저장소 설정에 실제로 적용된다")
    void prodCleanupCron_isApplied() {
        runner(true).run(context -> assertCleanupCron(context, PROD_CRON, PROD_CRON));
    }

    @Test
    @DisplayName("대조군: prod 값이 없으면 Spring Session 기본값(매분)이다 — 위 테스트가 값을 구분한다는 증거")
    void withoutProdYml_usesDefaultEveryMinute() {
        runner(false).run(context -> assertCleanupCron(context, null, BOOT_DEFAULT_CRON));
    }

    @Test
    @DisplayName("속성 이름을 한 글자만 틀려도 에러 없이 무시되어 기본값(매분)으로 돈다 — 그래서 이름을 검증한다")
    void misspelledPropertyName_isSilentlyIgnored() {
        runner(false)
                .withPropertyValues("spring.session.data.redis.cleanup-crone=" + PROD_CRON)
                .run(context -> assertCleanupCron(context, null, BOOT_DEFAULT_CRON));
    }

    @Test
    @DisplayName("application-prod.yml 은 정식 키 spring.session.data.redis.cleanup-cron 하나만 쓴다 (구 이름 spring.session.redis.* 아님)")
    void prodYml_usesCanonicalKeyOnly() {
        PropertySource<?> prod = prodYaml();

        assertThat(prod.getProperty("spring.session.data.redis.cleanup-cron")).isEqualTo(PROD_CRON);
        assertThat(prod.getProperty("spring.session.redis.cleanup-cron")).isNull();
    }
}
