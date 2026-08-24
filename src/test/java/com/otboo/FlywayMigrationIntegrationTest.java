package com.otboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
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
 *
 * 기본 엔티티 스캔 범위(com.otboo 전체)를 그대로 두면 테스트 전용 엔티티
 * (예: JpaAuditingTest의 TestAuditedEntity, com.otboo.global.common.entity 소속)까지
 * 실제 엔티티로 잡혀서 "V1 스크립트엔 없는 테이블"로 validate가 실패한다.
 * 그래서 실제 도메인 엔티티가 있는 패키지로만 스캔 범위를 좁힌다.
 */
@Testcontainers
@EntityScan(basePackages = "com.otboo.domain")
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

	@Autowired
	private JobRepository jobRepository;

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

	@Test
	void notificationEventIdMigrationIsApplied() throws Exception {
		try (Connection connection = dataSource.getConnection()) {

			// V11 Migration 성공 여부 확인
			try (PreparedStatement ps = connection.prepareStatement(
					"""
                    SELECT success
                    FROM flyway_schema_history
                    WHERE version = '11'
                    """
			);
				 ResultSet rs = ps.executeQuery()) {

				assertThat(rs.next())
						.as("V11 알림 event_id 마이그레이션 이력이 존재해야 한다")
						.isTrue();

				assertThat(rs.getBoolean("success"))
						.as("V11 마이그레이션이 성공으로 기록돼야 한다")
						.isTrue();
			}

			// event_id가 NOT NULL인지 확인
			try (PreparedStatement ps = connection.prepareStatement(
					"""
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'notifications'
                      AND column_name = 'event_id'
                    """
			);
				 ResultSet rs = ps.executeQuery()) {

				assertThat(rs.next())
						.as("notifications.event_id 컬럼이 존재해야 한다")
						.isTrue();

				assertThat(rs.getString("is_nullable"))
						.as("notifications.event_id는 NOT NULL이어야 한다")
						.isEqualTo("NO");
			}

			// event_id UNIQUE Constraint 확인
			try (PreparedStatement ps = connection.prepareStatement(
					"""
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = 'public'
                      AND table_name = 'notifications'
                      AND constraint_name = 'uq_notifications_event_id'
                      AND constraint_type = 'UNIQUE'
                    """
			);
				 ResultSet rs = ps.executeQuery()) {

				assertThat(rs.next()).isTrue();

				assertThat(rs.getInt(1))
						.as("notifications.event_id UNIQUE 제약이 존재해야 한다")
						.isEqualTo(1);
			}
		}
	}

	@Test
	void notificationEventIdUniqueConstraintRejectsDuplicates() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();

		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);

			try {
				try (PreparedStatement ps = connection.prepareStatement(
						"""
                        INSERT INTO users (
                            id,
                            email,
                            name,
                            password_hash
                        )
                        VALUES (?, ?, ?, ?)
                        """
				)) {
					ps.setObject(1, userId);
					ps.setString(
							2,
							"notification-idempotency-"
									+ UUID.randomUUID()
									+ "@otboo.io"
					);
					ps.setString(3, "test-user");
					ps.setString(4, "encoded-password");
					ps.executeUpdate();
				}

				try (PreparedStatement ps = connection.prepareStatement(
						"""
                        INSERT INTO notifications (
                            id,
                            event_id,
                            receiver_id,
                            title,
                            content,
                            level
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """
				)) {
					ps.setObject(1, UUID.randomUUID());
					ps.setObject(2, eventId);
					ps.setObject(3, userId);
					ps.setString(4, "첫 번째 알림");
					ps.setString(5, "알림 내용");
					ps.setString(6, "INFO");

					ps.executeUpdate();
				}

				// 동일 event_id를 가진 두 번째 알림은 DB가 거부
				try (PreparedStatement ps = connection.prepareStatement(
						"""
                        INSERT INTO notifications (
                            id,
                            event_id,
                            receiver_id,
                            title,
                            content,
                            level
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """
				)) {
					ps.setObject(1, UUID.randomUUID());
					ps.setObject(2, eventId);
					ps.setObject(3, userId);
					ps.setString(4, "두 번째 알림");
					ps.setString(5, "중복 이벤트");
					ps.setString(6, "INFO");

					assertThatThrownBy(ps::executeUpdate)
							.isInstanceOf(SQLException.class);
				}
			} finally {
				connection.rollback();
			}
		}
	}

	/**
	 * spring.batch.jdbc.initialize-schema=never 상태에서는 V1 마이그레이션이 만든
	 * BATCH_* 테이블/시퀀스가 "존재하기만" 하면 컨텍스트는 문제없이 뜬다. 하지만 그 테이블·시퀀스의
	 * 컬럼/이름이 실제 Spring Batch 5.2.6 런타임이 기대하는 계약과 다르면 잡아내지 못한다.
	 * 그래서 JobRepository로 실제 채번·저장 경로(createJobExecution → add(StepExecution))를
	 * 직접 실행해서, BATCH_JOB_SEQ/BATCH_JOB_EXECUTION_SEQ/BATCH_STEP_EXECUTION_SEQ와
	 * execution context 저장(BATCH_STEP_EXECUTION_CONTEXT)까지 검증한다.
	 */
	@Test
	void jobRepositoryExercisesBatchMetadataSchema() throws Exception {
		// given: 재실행/재시도 시 JobInstance 유일성 제약(JOB_NAME, JOB_KEY)에 걸리지 않도록 매번 고유한 파라미터 사용
		JobParameters jobParameters = new JobParametersBuilder()
			.addString("runId", UUID.randomUUID().toString())
			.toJobParameters();

		// when: BATCH_JOB_SEQ로 JobInstance를, BATCH_JOB_EXECUTION_SEQ로 JobExecution을 채번하며 저장
		JobExecution jobExecution = jobRepository.createJobExecution(
			"flywayMigrationVerificationJob", jobParameters);

		StepExecution stepExecution = new StepExecution("flywayMigrationVerificationStep", jobExecution);
		stepExecution.getExecutionContext().putString("checkKey", "checkValue");
		// BATCH_STEP_EXECUTION_SEQ로 채번하며 저장 + execution context를 BATCH_STEP_EXECUTION_CONTEXT에 저장
		jobRepository.add(stepExecution);

		// then: 채번 자체가 성공했는지 (시퀀스 계약 확인)
		assertThat(jobExecution.getJobId())
			.as("BATCH_JOB_SEQ로 JobInstance PK가 채번돼야 한다")
			.isNotNull();
		assertThat(jobExecution.getId())
			.as("BATCH_JOB_EXECUTION_SEQ로 JobExecution PK가 채번돼야 한다")
			.isNotNull();
		assertThat(stepExecution.getId())
			.as("BATCH_STEP_EXECUTION_SEQ로 StepExecution PK가 채번돼야 한다")
			.isNotNull();

		try (Connection connection = dataSource.getConnection()) {
			// BATCH_JOB_EXECUTION_PARAMS 컬럼 계약 확인 (JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_VALUE)
			try (PreparedStatement ps = connection.prepareStatement(
				"SELECT PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS "
					+ "WHERE JOB_EXECUTION_ID = ? AND PARAMETER_NAME = 'runId'")) {
				ps.setLong(1, jobExecution.getId());
				try (ResultSet rs = ps.executeQuery()) {
					assertThat(rs.next())
						.as("BATCH_JOB_EXECUTION_PARAMS에 job parameter가 저장돼야 한다")
						.isTrue();
					assertThat(rs.getString("PARAMETER_VALUE"))
						.isEqualTo(jobParameters.getString("runId"));
				}
			}

			// BATCH_STEP_EXECUTION_CONTEXT에 execution context가 실제로 직렬화 저장됐는지 확인
			// (SHORT_CONTEXT는 Java 직렬화 후 Base64로 저장되므로, 원문 그대로가 아니라 디코딩해서 확인한다)
			try (PreparedStatement ps = connection.prepareStatement(
				"SELECT SHORT_CONTEXT FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID = ?")) {
				ps.setLong(1, stepExecution.getId());
				try (ResultSet rs = ps.executeQuery()) {
					assertThat(rs.next())
						.as("BATCH_STEP_EXECUTION_CONTEXT에 execution context가 저장돼야 한다")
						.isTrue();
					String shortContext = rs.getString("SHORT_CONTEXT");
					String decoded = new String(
						Base64.getDecoder().decode(shortContext), StandardCharsets.ISO_8859_1);
					assertThat(decoded).contains("checkKey", "checkValue");
				}
			}
		}
	}
}
