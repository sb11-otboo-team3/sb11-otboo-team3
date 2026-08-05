package com.otboo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * application-test.yaml은 H2 + Flyway 비활성화(create-drop)로 돌기 때문에
 * "Flyway가 V1__init_schema.sql로 실제 PostgreSQL 스키마를 만들고,
 *  그 스키마가 JPA 엔티티와 정확히 일치하는지"는 검증되지 않는다.
 * 이 테스트는 실제 PostgreSQL 컨테이너 위에서 Flyway 마이그레이션을 그대로 실행하고,
 * Hibernate ddl-auto=validate로 엔티티-스키마 불일치를 잡아낸다.
 */
@Testcontainers
@SpringBootTest(properties = {
	"spring.flyway.enabled=true",
	"spring.jpa.hibernate.ddl-auto=validate",
	"spring.batch.jdbc.initialize-schema=never"
})
class FlywayMigrationIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads_whenFlywayMigratesRealPostgresAndHibernateValidates() {
		// 컨텍스트가 정상 기동되는 것 자체가 "Flyway 마이그레이션 성공 + Hibernate 엔티티-스키마 일치"를 의미한다.
	}

	@Test
	void flywayRecordsV1MigrationAsSuccessful() throws Exception {
		try (Connection connection = dataSource.getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(
				"SELECT success FROM flyway_schema_history WHERE version = '1'")) {

			assertThat(resultSet.next())
				.as("V1__init_schema.sql 마이그레이션 이력이 존재해야 한다")
				.isTrue();
			assertThat(resultSet.getBoolean("success"))
				.as("V1 마이그레이션이 성공으로 기록돼야 한다")
				.isTrue();
		}
	}
}