package com.otboo.global.infrastructure.kafka.smoke;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@ConditionalOnProperty(
        prefix = "app.kafka.smoke",
        name = "enabled",
        havingValue = "true"
)
public class KafkaMskSmokeProbe implements ApplicationRunner {

    private static final long PRODUCE_TIMEOUT_SECONDS = 10L;
    private static final long ASSIGNMENT_TIMEOUT_SECONDS = 10L;
    private static final long CONSUME_TIMEOUT_SECONDS = 30L;

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private static final String GROUP_ID_PREFIX =
            "otboo-infrastructure-smoke-consumer-";

    private static final String CLIENT_ID_SUFFIX = "msk-smoke";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ConsumerFactory<Object, Object> consumerFactory;
    private final String topic;

    public KafkaMskSmokeProbe(
            KafkaTemplate<Object, Object> kafkaTemplate,
            ConsumerFactory<Object, Object> consumerFactory,
            @Value("${app.kafka.smoke.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.consumerFactory = consumerFactory;
        this.topic = topic;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String groupId = GROUP_ID_PREFIX + correlationId;
        String message = "msk-smoke:" + correlationId;

        try (Consumer<Object, Object> consumer =
                     consumerFactory.createConsumer(
                             groupId,
                             CLIENT_ID_SUFFIX
                     )) {

            consumer.subscribe(List.of(topic));

            Set<TopicPartition> assignment =
                    waitForAssignment(consumer);

            moveToCurrentEnd(consumer, assignment);

            kafkaTemplate
                    .send(topic, correlationId, message)
                    .get(PRODUCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            consumeSmokeMessage(consumer, message);
        }
    }

    private Set<TopicPartition> waitForAssignment(
            Consumer<Object, Object> consumer
    ) {
        long deadline =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(
                        ASSIGNMENT_TIMEOUT_SECONDS
                );

        while (System.nanoTime() < deadline) {
            consumer.poll(POLL_TIMEOUT);

            Set<TopicPartition> assignment =
                    consumer.assignment();

            if (!assignment.isEmpty()) {
                return assignment;
            }
        }

        throw new IllegalStateException(
                "Kafka MSK smoke test failed: "
                        + "consumer partition assignment timed out"
        );
    }

    private void moveToCurrentEnd(
            Consumer<Object, Object> consumer,
            Set<TopicPartition> assignment
    ) {
        consumer.seekToEnd(assignment);

        // seekToEnd는 lazy하게 평가되니까
        // smoke 메시지를 발행하기 전에 position 호출로 현재 end 위치를 확정
        for (TopicPartition partition : assignment) {
            consumer.position(partition);
        }
    }

    private void consumeSmokeMessage(
            Consumer<Object, Object> consumer,
            String message
    ) {
        long deadline =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(
                        CONSUME_TIMEOUT_SECONDS
                );

        while (System.nanoTime() < deadline) {
            ConsumerRecords<Object, Object> records =
                    consumer.poll(POLL_TIMEOUT);

            for (ConsumerRecord<Object, Object> record : records) {
                if (message.equals(record.value())) {
                    log.info(
                            "Kafka MSK smoke test success. "
                                    + "topic={}, partition={}, offset={}",
                            record.topic(),
                            record.partition(),
                            record.offset()
                    );
                    return;
                }
            }
        }

        throw new IllegalStateException(
                "Kafka MSK smoke test failed: "
                        + "produced message was not consumed within timeout"
        );
    }
}