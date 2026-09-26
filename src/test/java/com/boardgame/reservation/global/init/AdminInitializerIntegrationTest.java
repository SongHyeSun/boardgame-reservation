package com.boardgame.reservation.global.init;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.global.security.MemberSessionInvalidator;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부트스트랩이 실제 DB(H2)에서 기존 게임의 소유자를 SUPER_ADMIN 으로 채우는지 검증한다 (단위 테스트는 repository 를 mock 으로 대체).
 * 승격(같은 트랜잭션의 dirty 변경)과 벌크 UPDATE 가 함께 반영되는지도 확인한다.
 * 테스트 application.yml 의 ADMIN_EMAIL 은 비어 있어 컨텍스트의 부트스트랩은 동작하지 않으므로 직접 만들어 호출한다.
 */
class AdminInitializerIntegrationTest extends RedisIntegrationTestSupport {

    private static final String ROOT_EMAIL = "root@test.com";

    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    MemberSessionInvalidator sessionInvalidator;
    @Autowired
    PlatformTransactionManager transactionManager;

    AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminInitializer(memberRepository, boardGameRepository, passwordEncoder, sessionInvalidator);
        ReflectionTestUtils.setField(initializer, "adminEmail", ROOT_EMAIL);
        ReflectionTestUtils.setField(initializer, "adminPassword", "secret-pw");
    }

    /** ApplicationRunner 로 호출될 때와 같이 하나의 트랜잭션으로 실행 */
    private void runBootstrap() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> initializer.run(null));
    }

    private BoardGame legacyGame(String name) {
        return boardGameRepository.save(BoardGame.create(name, 2, 4, 30, Difficulty.EASY, "옛날 게임"));
    }

    private Long ownerIdOf(BoardGame game) {
        Member owner = boardGameRepository.findWithOwnerById(game.getId()).orElseThrow().getCreatedBy();
        return owner == null ? null : owner.getId();
    }

    @Test
    @DisplayName("기존 ADMIN 계정을 승격하면서 소유자 없는 게임을 그 계정 소유로 채운다 (승격과 백필이 함께 반영)")
    void promotesExistingAdmin_andAssignsLegacyGames() {
        Member existing = memberRepository.save(Member.createAdmin(ROOT_EMAIL, "old-encoded", "관리자"));
        Member otherAdmin = saveAdmin("other");
        BoardGame legacyA = legacyGame("Old A");
        BoardGame legacyB = legacyGame("Old B");
        BoardGame owned = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Owned", 2, 4, 30, Difficulty.EASY, "설명", true, false, 1), otherAdmin));

        runBootstrap();

        assertThat(memberRepository.findById(existing.getId()).orElseThrow().getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(ownerIdOf(legacyA)).isEqualTo(existing.getId());
        assertThat(ownerIdOf(legacyB)).isEqualTo(existing.getId());
        // 이미 소유자가 있는 게임은 건드리지 않는다
        assertThat(ownerIdOf(owned)).isEqualTo(otherAdmin.getId());
    }

    @Test
    @DisplayName("계정이 없으면 SUPER_ADMIN 을 만들고 소유자 없는 게임을 그 계정 소유로 채운다")
    void createsSuperAdmin_andAssignsLegacyGames() {
        BoardGame legacy = legacyGame("Old");

        runBootstrap();

        Member root = memberRepository.findByEmail(ROOT_EMAIL).orElseThrow();
        assertThat(root.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(ownerIdOf(legacy)).isEqualTo(root.getId());
    }

    @Test
    @DisplayName("다시 기동해도(이미 SUPER_ADMIN) 결과는 같고, 그 뒤 새로 생긴 소유자 없는 게임도 채워진다")
    void rerun_isIdempotent() {
        BoardGame first = legacyGame("Old 1");
        runBootstrap();
        Long rootId = memberRepository.findByEmail(ROOT_EMAIL).orElseThrow().getId();
        BoardGame second = legacyGame("Old 2");

        runBootstrap();

        assertThat(memberRepository.findByEmail(ROOT_EMAIL).orElseThrow().getId()).isEqualTo(rootId);
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(ownerIdOf(first)).isEqualTo(rootId);
        assertThat(ownerIdOf(second)).isEqualTo(rootId);
    }
}
