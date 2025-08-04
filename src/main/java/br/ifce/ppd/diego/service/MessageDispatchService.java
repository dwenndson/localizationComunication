package br.ifce.ppd.diego.service;

import br.ifce.ppd.diego.locationchat.dto.DispatchDTO;
import br.ifce.ppd.diego.locationchat.dto.MessageDTO;
import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageDispatchService {

    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaProducerService kafkaProducer;

    public MessageDispatchService(SimpMessagingTemplate messagingTemplate, KafkaProducerService kafkaProducer) {
        this.messagingTemplate = messagingTemplate;
        this.kafkaProducer = kafkaProducer;
    }

    public void dispatch(DispatchDTO dispatch) {
        MessageDTO message = dispatch.getMessage();

        if (dispatch.getCommunicationType() == CommunicationType.SYNC) {
            // Comunicação Síncrona: envia via WebSocket.
            String destination = "/user/" + message.getRecipient() + "/queue/messages";
            System.out.println("Despacho SYNC para: " + destination);
            messagingTemplate.convertAndSend(destination, message);
        } else {
            // Comunicação Assíncrona: envia via Kafka.
            String topic = "user-inbox-" + message.getRecipient();
            System.out.println("Despacho ASYNC para o tópico Kafka: " + topic);
            kafkaProducer.sendMessage(topic, message);
        }
    }
}
