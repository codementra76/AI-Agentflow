package com.agentflow.entity;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchFlywayMigrationTest {

    @Test
    @DisplayName("Verify Flyway migrations V1 through V6 execute without errors")
    void shouldExecuteAllMigrationsIncludingV6() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("6");
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(6);
    }
}
