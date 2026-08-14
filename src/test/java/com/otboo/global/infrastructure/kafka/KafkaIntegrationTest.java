package com.otboo.global.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

@SpringBootTest(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = KafkaIntegrationTest.TOPIC,
        kraft = true
)
@DirtiesContext
@Import(KafkaIntegrationTest.KafkaTestConfig.class)
class KafkaIntegrationTest {

    static final String TOPIC = "otboo.kafka.smoke-test.v1";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    @Test
    void producer가_보낸_메시지를_consumer가_수신한다() throws Exception {
        // given
        String message = "kafka-smoke-test";

        // when
        kafkaTemplate.send(TOPIC, message).get(5, TimeUnit.SECONDS);

        // then
        boolean received = testKafkaConsumer.await(5, TimeUnit.SECONDS);

        assertThat(received).isTrue();
        assertThat(testKafkaConsumer.getMessage()).isEqualTo(message);
    }

    @TestConfiguration
    @EnableKafka
    static class KafkaTestConfig {

        @Bean
        TestKafkaConsumer testKafkaConsumer() {
            return new TestKafkaConsumer();
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
}