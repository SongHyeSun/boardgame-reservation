package com.boardgame.reservation.support;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PG 인프라 스모크: 정말 PostgreSQL 에 붙었는지, 격리 수준·스키마 생성·기본 저장/정리가 되는지 */
class PostgresIntegrationTestSupportTest extends PostgresIntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("H2 가 아니라 컨테이너의 PostgreSQL 16 에 연결된다")
    void connectsToRealPostgres() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);

        assertThat(version).startsWith("PostgreSQL 16");
    }

    @Test
    @DisplayName("기본 격리 수준은 READ COMMITTED (락 획득 후 조회가 앞선 커밋을 본다는 전제)")
    void isolationIsReadCommitted() {
        String isolation = jdbcTemplate.queryForObject("show transaction_isolation", String.class);

        assertThat(isolation).isEqualTo("read committed");
    }

    @Test
    @DisplayName("ddl-auto 로 전체 스키마가 PG 에 생성되고 회원·게임을 저장·조회할 수 있다")
    void schemaCreatedAndEntitiesPersist() {
        Member member = saveMember("pg");
        BoardGame game = boardGameRepository.save(
                BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "자원 교환"));

        assertThat(memberRepository.findById(member.getId())).isPresent();
        assertThat(boardGameRepository.findById(game.getId())).get()
                .extracting(BoardGame::getStock, BoardGame::isVisible)
                .containsExactly(1, true);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('member','board_game','party','party_member')",
                Integer.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("reservation 테이블이 PG 에 생성된다: 인덱스 (board_game_id, start_date, end_date), enum CHECK 2개, FK 2개")
    void reservationSchemaOnPostgres() {
        String indexDef = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where tablename = 'reservation' and indexname = 'idx_reservation_game_period'",
                String.class);
        List<String> checks = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid = 'reservation'::regclass and contype = 'c' order by conname",
                String.class);
        Integer foreignKeys = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conrelid = 'reservation'::regclass and contype = 'f'",
                Integer.class);

        assertThat(indexDef).contains("(board_game_id, start_date, end_date)");
        assertThat(checks).containsExactly("reservation_cancel_reason_check", "reservation_status_check");
        assertThat(foreignKeys).isEqualTo(2);
    }

    @Test
    @DisplayName("이전 테스트의 데이터는 정리된다 (테스트 간 격리)")
    void previousDataIsCleanedUp() {
        assertThat(memberRepository.count()).isZero();
        assertThat(boardGameRepository.count()).isZero();
    }
}
