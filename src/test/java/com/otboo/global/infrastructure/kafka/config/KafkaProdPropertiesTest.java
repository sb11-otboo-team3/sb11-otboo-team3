package com.otboo.global.infrastructure.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.mock.env.MockEnvironment;

class KafkaProdPropertiesTest {

    private static final String SECURITY_PROTOCOL_PROPERTY =
            "spring.kafka.security.protocol";

    @Test
    @DisplayName("prod Kafka 보안 프로토콜의 기본값은 기존 MSK용 SASL_SSL이다")
    void defaultsToSaslSslForMskRollback() throws IOException {
        // given
        MockEnvironment environment = loadProdEnvironment();

        // when
        String protocol =
                environment.getProperty(SECURITY_PROTOCOL_PROPERTY);

        // then
        assertThat(protocol).isEqualTo("SASL_SSL");
    }

    @Test
    @DisplayName("prod Kafka 보안 프로토콜은 환경변수로 PLAINTEXT 전환할 수 있다")
    void overridesSecurityProtocolToPlaintext() throws IOException {
        // given
        MockEnvironment environment = loadProdEnvironment();
        environment.setProperty(
                "KAFKA_SECURITY_PROTOCOL",
                "PLAINTEXT"
        );

        // when
        String protocol =
                environment.getProperty(SECURITY_PROTOCOL_PROPERTY);

        // then
        assertThat(protocol).isEqualTo("PLAINTEXT");
    }

    private MockEnvironment loadProdEnvironment() throws IOException {
        MockEnvironment environment = new MockEnvironment();

        YamlPropertySourceLoader loader =
                new YamlPropertySourceLoader();

        List<PropertySource<?>> propertySources =
                loader.load(
                        "application-prod",
                        new ClassPathResource("application-prod.yaml")
                );

        propertySources.forEach(
                propertySource ->
                        environment
                                .getPropertySources()
                                .addLast(propertySource)
        );

        return environment;
    }
}
