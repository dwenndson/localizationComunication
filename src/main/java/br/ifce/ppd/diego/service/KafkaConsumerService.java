package br.ifce.ppd.diego.service;


import br.ifce.ppd.diego.locationchat.dto.MessageDTO;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final SimpMessagingTemplate messagingTemplate;

    public KafkaConsumerService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topicPattern = "user-inbox-.*", groupId = "location-chat-group")
    public void consumeOfflineMessage(MessageDTO message) {
        String destination = "/user/" + message.getRecipient() + "/queue/messages";
        System.out.println("Mensagem ASYNC consumida do Kafka para " + message.getRecipient() + ". A tentar entregar em: " + destination);
        messagingTemplate.convertAndSend(destination, message);
    }
}
