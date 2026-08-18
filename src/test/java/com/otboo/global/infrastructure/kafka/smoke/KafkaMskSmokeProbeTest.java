package com.otboo.global.infrastructure.kafka.smoke;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaMskSmokeProbeTest {

    private static final String TOPIC =
            "otboo.infrastructure.connectivity-checked.v1";

    @Test
    @DisplayName("MSK 스모크 검증은 기존 레코드를 건너뛴 후 새 메시지를 발행하고 소비한다")
    void verifiesNewSmokeMessageCanBeConsumed() throws Exception {
        KafkaTemplate<Object, Object> kafkaTemplate =
                mock(KafkaTemplate.class);

        ConsumerFactory<Object, Object> consumerFactory =
                mock(ConsumerFactory.class);

        Consumer<Object, Object> consumer =
                mock(Consumer.class);

        TopicPartition topicPartition =
                new TopicPartition(TOPIC, 0);

        Set<TopicPartition> assignment =
                Set.of(topicPartition);

        AtomicReference<Object> sentKey =
                new AtomicReference<>();

        AtomicReference<Object> sentValue =
                new AtomicReference<>();

        when(consumerFactory.createConsumer(
                anyString(),
                eq("msk-smoke")
        )).thenReturn(consumer);

        when(consumer.assignment())
                .thenReturn(Set.of())
                .thenReturn(assignment)
                .thenReturn(assignment);

        when(consumer.position(topicPartition))
                .thenReturn(0L);

        when(kafkaTemplate.send(
                eq(TOPIC),
                any(),
                any()
        )).thenAnswer(invocation -> {
            sentKey.set(invocation.getArgument(1));
            sentValue.set(invocation.getArgument(2));

            return CompletableFuture.completedFuture(
                    mock(SendResult.class)
            );
        });

        when(consumer.poll(any(Duration.class)))
                .thenAnswer(invocation -> {
                    if (sentValue.get() == null) {
                        return new ConsumerRecords<>(Map.of());
                    }

                    ConsumerRecord<Object, Object> record =
                            new ConsumerRecord<>(
                                    TOPIC,
                                    0,
                                    0L,
                                    sentKey.get(),
                                    sentValue.get()
                            );

                    return new ConsumerRecords<>(
                            Map.of(
                                    topicPartition,
                                    List.of(record)
                            )
                    );
                });

        KafkaMskSmokeProbe probe =
                new KafkaMskSmokeProbe(
                        kafkaTemplate,
                        consumerFactory,
                        TOPIC
                );

        probe.run(null);

        verify(consumer).subscribe(List.of(TOPIC));
        verify(consumer).seekToEnd(assignment);
        verify(consumer).position(topicPartition);

        verify(kafkaTemplate).send(
                eq(TOPIC),
                any(),
                any()
        );

        verify(consumer).close();

        InOrder inOrder =
                inOrder(consumer, kafkaTemplate);

        inOrder.verify(consumer).seekToEnd(assignment);
        inOrder.verify(consumer).position(topicPartition);
        inOrder.verify(kafkaTemplate).send(
                eq(TOPIC),
                any(),
                any()
        );
    }
}