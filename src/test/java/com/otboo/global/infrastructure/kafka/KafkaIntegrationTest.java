package com.otboo.global.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@SpringBootTest(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 2,
        topics = {
                KafkaIntegrationTest.TOPIC,
                KafkaIntegrationTest.FAILURE_TOPIC,
                KafkaIntegrationTest.FAILURE_DLT_TOPIC
        },
        kraft = true
)
@DirtiesContext
@Import(KafkaIntegrationTest.KafkaTestConfig.class)
class KafkaIntegrationTest {

    static final String TOPIC = "otboo.kafka.smoke-test.v1";

    static final String FAILURE_TOPIC = "otboo.kafka.retry-test.v1";

    static final String FAILURE_DLT_TOPIC = FAILURE_TOPIC + ".dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    @Autowired
    private FailingKafkaConsumer failingKafkaConsumer;

    @Autowired
    private DltKafkaConsumer dltKafkaConsumer;

    @Test
    @DisplayName("Producer가 전송한 메시지를 Consumer가 정상적으로 수신한다")
    void produceAndConsumeMessage() throws Exception {
        // given
        String message = "kafka-smoke-test";

        // when
        kafkaTemplate.send(TOPIC, message).get(5, TimeUnit.SECONDS);

        // then
        boolean received = testKafkaConsumer.await(5, TimeUnit.SECONDS);

        assertThat(received).isTrue();
        assertThat(testKafkaConsumer.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("Consumer 처리 실패가 재시도 횟수를 초과하면 같은 Partition의 DLT로 전송한다")
    void sendToDltAfterRetryFailure() throws Exception {
        // given
        String message = "kafka-retry-test";
        int partition = 1;

        // when
        kafkaTemplate.send(FAILURE_TOPIC, partition, null, message)
                .get(5, TimeUnit.SECONDS);

        // then
        boolean retried = failingKafkaConsumer.await(5, TimeUnit.SECONDS);
        boolean sentToDlt = dltKafkaConsumer.await(5, TimeUnit.SECONDS);

        assertThat(retried).isTrue();
        assertThat(failingKafkaConsumer.getAttemptCount()).isEqualTo(3);

        assertThat(sentToDlt).isTrue();
        assertThat(dltKafkaConsumer.getMessage()).isEqualTo(message);
        assertThat(dltKafkaConsumer.getPartition()).isEqualTo(partition);
    }

    @TestConfiguration
    @EnableKafka
    static class KafkaTestConfig {

        @Bean
        TestKafkaConsumer testKafkaConsumer() {
            return new TestKafkaConsumer();
        }

        @Bean
        FailingKafkaConsumer failingKafkaConsumer() {
            return new FailingKafkaConsumer();
        }

        @Bean
        DltKafkaConsumer dltKafkaConsumer() {
            return new DltKafkaConsumer();
        }
    }

    static class TestKafkaConsumer {

        private final CountDownLatch latch = new CountDownLatch(1);

        private final AtomicReference<String> message = new AtomicReference<>();

        @KafkaListener(
                topics = TOPIC,
                groupId = "otboo-kafka-smoke-test-consumer"
        )
        void consume(String payload) {
            message.set(payload);
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }

        String getMessage() {
            return message.get();
        }
    }

    static class FailingKafkaConsumer {

        private final CountDownLatch latch = new CountDownLatch(3);

        private final AtomicInteger attemptCount = new AtomicInteger();

        @KafkaListener(
                topics = FAILURE_TOPIC,
                groupId = "otboo-kafka-retry-test-consumer"
        )
        void consume(String payload) {
            attemptCount.incrementAndGet();
            latch.countDown();

            throw new IllegalStateException("Kafka 재시도 검증용 예외");
        }

        boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }

        int getAttemptCount() {
            return attemptCount.get();
        }
    }

    static class DltKafkaConsumer {

        private final CountDownLatch latch = new CountDownLatch(1);

        private final AtomicReference<String> message = new AtomicReference<>();

        private final AtomicInteger partition = new AtomicInteger(-1);

        @KafkaListener(
                topics = FAILURE_DLT_TOPIC,
                groupId = "otboo-kafka-dlt-test-consumer"
        )
        void consume(ConsumerRecord<String, String> record) {
            message.set(record.value());
            partition.set(record.partition());
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }

        String getMessage() {
            return message.get();
        }

        int getPartition() {
            return partition.get();
        }
    }
}