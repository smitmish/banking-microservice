package com.banking.transaction.config;

import com.banking.transaction.dto.TransactionEventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, TransactionEventDto> consumerFactory() {
        // Collect base properties from environment configs
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());

        // 1. Set up the target underlying JSON Deserializer
        JacksonJsonDeserializer<TransactionEventDto> delegateDeserializer =
                new JacksonJsonDeserializer<>(TransactionEventDto.class);

        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.banking.transaction.dto,com.banking.account.dto");
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Ignore String headers from Outbox Relayer
        delegateDeserializer.configure(props, false);

        // 2. Wrap it inside an ErrorHandlingDeserializer to prevent poll loop application crashes
        ErrorHandlingDeserializer<TransactionEventDto> errorHandlingValueDeserializer =
                new ErrorHandlingDeserializer<>(delegateDeserializer);

        // Explicitly map keys and values to their safe deserialization managers
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingValueDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEventDto> kafkaListenerContainerFactory(
            ConsumerFactory<String, TransactionEventDto> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, TransactionEventDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // 1. Update the error handler to look for our dedicated DLT template
    @Bean
    public CommonErrorHandler errorHandler(
            org.springframework.kafka.core.KafkaTemplate<String, byte[]> deadLetterTemplate) {

        // Retries processing 3 times, spaced out by 2 seconds
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);

        // Instructs the recoverer to forward the message as raw, unaltered bytes [B
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    // 2. Define an explicit raw byte producer factory for the dead-letter engine
    @Bean
    public org.springframework.kafka.core.ProducerFactory<String, byte[]> deadLetterProducerFactory() {
        Map<String, Object> configProps = new HashMap<>(kafkaProperties.buildProducerProperties());

        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);

        // Fixes [B ClassCastException by cleanly supporting raw bytes
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArraySerializer.class);

        return new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(configProps);
    }

    // 3. Create the dedicated DLT Template bean instance
    @Bean
    public org.springframework.kafka.core.KafkaTemplate<String, byte[]> deadLetterTemplate(
            org.springframework.kafka.core.ProducerFactory<String, byte[]> deadLetterProducerFactory) {
        return new org.springframework.kafka.core.KafkaTemplate<>(deadLetterProducerFactory);
    }
}