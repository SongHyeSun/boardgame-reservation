package com.boardgame.reservation.support;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway 마이그레이션(V1~) 검증. 빈 PostgreSQL 에 마이그레이션만으로 스키마를 만들고 Hibernate validate 로 엔티티와 대조한다.
 * - 엔티티를 바꾸고 마이그레이션을 빼먹으면 validate 가 실패한다 (컬럼·타입 불일치).
 * - validate 는 CHECK 제약을 보지 않으므로, enum 값이 CHECK 를 통과하는지는 아래 테스트가 따로 확인한다.
 * PostgresIntegrationTestSupport 를 상속하지 않는 이유: 그쪽은 create-drop 으로 스키마를 만들고 컨텍스트를 공유한다.
 * (이 클래스는 자체 컨테이너·컨텍스트를 쓴다. Redis 는 필요 없다.)
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationPostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("V1 이 적용되고 엔티티 스키마 validate 를 통과해 컨텍스트가 기동된다")
    void migrationAppliedAndSchemaValid() {
        // 여기까지 왔다는 것 자체가 ddl-auto=validate 통과. 히스토리도 확인한다.
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versions).startsWith("1");
    }

    @Test
    @DisplayName("모든 enum 컬럼의 모든 값이 CHECK 제약을 통과해 INSERT 된다 (enum 값을 추가하고 CHECK 를 안 고치면 실패)")
    void everyEnumValueSatisfiesCheckConstraint() {
        List<EnumColumn> enumColumns = findEnumColumns();
        assertThat(enumColumns).as("enum 컬럼을 하나도 찾지 못함 — 탐색 로직 점검 필요").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (EnumColumn column : enumColumns) {
            String scratch = createScratchTable(column.table());
            for (Object constant : column.enumType().getEnumConstants()) {
                try {
                    jdbcTemplate.update("insert into " + scratch + " (" + column.column() + ") values (?)",
                            ((Enum<?>) constant).name());
                } catch (DataIntegrityViolationException e) {
                    failures.add(column.table() + "." + column.column() + " rejects " + constant);
                }
            }
        }

        assertThat(failures).as("CHECK 제약이 거부한 enum 값 — 새 마이그레이션에서 CHECK 를 함께 변경해야 함").isEmpty();
    }

    @Test
    @DisplayName("CHECK 제약은 실제로 살아 있다 (enum 에 없는 값은 거부) — 위 테스트가 항상 통과하는 빈 검사가 아님을 보증")
    void checkConstraintRejectsUnknownValue() {
        String scratch = createScratchTable("party");

        assertThatThrownBy(() ->
                jdbcTemplate.update("insert into " + scratch + " (status) values ('NOT_A_STATUS')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 엔티티의 @Enumerated 필드 → (테이블, 컬럼, enum 타입). 컬럼명은 Spring 기본 네이밍(camelCase → snake_case), 실제 존재도 검증한다. */
    private List<EnumColumn> findEnumColumns() {
        List<EnumColumn> result = new ArrayList<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            Table table = entity.getJavaType().getAnnotation(Table.class);
            String tableName = table != null ? table.name() : toSnakeCase(entity.getName());
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                Class<?> type = attribute.getJavaType();
                if (!type.isEnum()) {
                    continue;
                }
                String columnName = toSnakeCase(attribute.getName());
                Integer exists = jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns where table_name = ? and column_name = ?",
                        Integer.class, tableName, columnName);
                assertThat(exists).as("컬럼 %s.%s 를 찾지 못함(네이밍 가정 확인)", tableName, columnName).isEqualTo(1);
                result.add(new EnumColumn(tableName, columnName, type));
            }
        }
        return result;
    }

    /** CHECK 는 복사하고 NOT NULL·FK 는 뺀 임시 테이블 — 대상 컬럼 하나만 넣어 CHECK 만 검증한다 */
    private String createScratchTable(String table) {
        String scratch = "scratch_" + table + "_" + System.nanoTime();
        jdbcTemplate.execute("create table " + scratch + " (like public." + table + " including constraints)");
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = ? and is_nullable = 'NO'",
                String.class, scratch);
        for (String column : columns) {
            jdbcTemplate.execute("alter table " + scratch + " alter column " + column + " drop not null");
        }
        return scratch;
    }

    private static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private record EnumColumn(String table, String column, Class<?> enumType) {
    }
}
