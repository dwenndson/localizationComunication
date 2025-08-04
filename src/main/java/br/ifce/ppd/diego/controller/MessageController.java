package br.ifce.ppd.diego.controller;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.locationchat.dto.DispatchDTO;
import br.ifce.ppd.diego.locationchat.dto.MessageDTO;
import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import br.ifce.ppd.diego.locationchat.identity.User;
import br.ifce.ppd.diego.repository.UserRepository;
import br.ifce.ppd.diego.service.MessageDispatchService;
import br.ifce.ppd.diego.utils.LocationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final UserRepository userRepository;
    private final MessageDispatchService dispatchService;

    public MessageController(UserRepository userRepository, MessageDispatchService dispatchService) {
        this.userRepository = userRepository;
        this.dispatchService = dispatchService;
    }

    @PostMapping("/send")
    public ResponseEntity<Void> sendMessage(@RequestBody MessageDTO message) {
        // Encontra o remetente e o destinatário no repositório.
        var senderOpt = userRepository.findByUsername(message.getSender());
        var recipientOpt = userRepository.findByUsername(message.getRecipient());

        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        User sender = senderOpt.get();
        User recipient = recipientOpt.get();

        // Calcula a distância entre eles.
        double distance = LocationUtils.calculateDistance(
                sender.getLatitude(), sender.getLongitude(),
                recipient.getLatitude(), recipient.getLongitude()
        );

        CommunicationType type = (recipient.getStatus() == UserStatus.ONLINE && distance <= sender.getCommunicationRadiusKm())
                ? CommunicationType.SYNC
                : CommunicationType.ASYNC;

        DispatchDTO dispatchDTO = new DispatchDTO(message, type);
        dispatchService.dispatch(dispatchDTO);

        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
}