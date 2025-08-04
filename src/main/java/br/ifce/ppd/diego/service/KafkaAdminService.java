package br.ifce.ppd.diego.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

@Service
public class KafkaAdminService {

    private final KafkaAdmin kafkaAdmin;

    public KafkaAdminService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public void createTopic(String topicName) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);

            CreateTopicsResult result = adminClient.createTopics(Collections.singleton(newTopic));

            result.all().get();

            System.out.println("Tópico Kafka criado com sucesso e confirmado: " + topicName);

        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                System.out.println("Tópico Kafka '" + topicName + "' já existe. Nenhuma ação necessária.");
            } else {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("Falha crítica ao criar o tópico Kafka: " + topicName, e);
            }
        }
    }
}
