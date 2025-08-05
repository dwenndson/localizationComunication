package br.ifce.ppd.diego.controller;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.locationchat.dto.ContactDTO;
import br.ifce.ppd.diego.locationchat.dto.MessageDTO;
import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import br.ifce.ppd.diego.locationchat.identity.User;
import br.ifce.ppd.diego.repository.UserRepository;
import br.ifce.ppd.diego.service.KafkaAdminService;
import br.ifce.ppd.diego.service.NotificationService;
import br.ifce.ppd.diego.utils.LocationUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final KafkaAdminService kafkaAdminService;
    private final Map<String, Object> kafkaConsumerProperties;
    private final NotificationService notificationService;

    public UserController(UserRepository userRepository, KafkaAdminService kafkaAdminService,
                          KafkaProperties kafkaProperties, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.kafkaAdminService = kafkaAdminService;
        this.kafkaConsumerProperties = kafkaProperties.buildConsumerProperties(null);
        this.notificationService = notificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody User loginRequest) {
        boolean isNewUser = userRepository.findByUsername(loginRequest.getUsername()).isEmpty();
        User user = new User(
                loginRequest.getUsername(),
                loginRequest.getLatitude(),
                loginRequest.getLongitude(),
                loginRequest.getCommunicationRadiusKm()
        );
        userRepository.save(user);
        if (isNewUser) {
            kafkaAdminService.createTopic("user-inbox-" + user.getUsername());
        }
        notificationService.notifyAllOnlineUsersOfUpdate(user.getUsername());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{username}/location")
    public ResponseEntity<Void> updateLocation(@PathVariable String username, @RequestBody Map<String, Double> location) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setLatitude(location.get("latitude"));
                    user.setLongitude(location.get("longitude"));
                    userRepository.save(user);
                    notificationService.notifyContactsOfUpdate(username);
                    return new ResponseEntity<Void>(HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{username}/radius")
    public ResponseEntity<Void> updateRadius(@PathVariable String username, @RequestBody Map<String, Double> payload) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setCommunicationRadiusKm(payload.get("radius"));
                    userRepository.save(user);
                    notificationService.notifyContactsOfUpdate(username);
                    return new ResponseEntity<Void>(HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{username}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String username, @RequestBody Map<String, UserStatus> status) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setStatus(status.get("status"));
                    userRepository.save(user);
                    return new ResponseEntity<Void>(HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{username}/offline-messages")
    public ResponseEntity<List<MessageDTO>> getOfflineMessages(@PathVariable String username) {
        String topic = "user-inbox-" + username;

        Map<String, Object> props = new HashMap<>(this.kafkaConsumerProperties);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "temp-consumer-" + username + "-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<MessageDTO> valueDeserializer = new JsonDeserializer<>(MessageDTO.class, false);

        DefaultKafkaConsumerFactory<String, MessageDTO> factory = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );

        List<MessageDTO> messages = new ArrayList<>();
        try (Consumer<String, MessageDTO> consumer = factory.createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));
            consumer.poll(Duration.ofMillis(100));
            ConsumerRecords<String, MessageDTO> records = consumer.poll(Duration.ofMillis(1000));
            records.forEach(record -> messages.add(record.value()));
            consumer.commitSync();
        }

        System.out.println("Encontradas " + messages.size() + " mensagens offline para " + username);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{username}/contacts")
    public ResponseEntity<List<ContactDTO>> getContacts(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(currentUser -> {
                    List<ContactDTO> contacts = currentUser.getContacts().stream()
                            .map(userRepository::findByUsername)
                            .filter(java.util.Optional::isPresent)
                            .map(java.util.Optional::get)
                            .map(contactUser -> {
                                double distance = LocationUtils.calculateDistance(
                                        currentUser.getLatitude(), currentUser.getLongitude(),
                                        contactUser.getLatitude(), contactUser.getLongitude()
                                );
                                CommunicationType type = (contactUser.getStatus() == UserStatus.ONLINE && distance <= currentUser.getCommunicationRadiusKm())
                                        ? CommunicationType.SYNC
                                        : CommunicationType.ASYNC;
                                return new ContactDTO(contactUser.getUsername(), contactUser.getStatus(), distance, type);
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(contacts);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{username}/contacts")
    public ResponseEntity<Void> addContact(@PathVariable String username, @RequestBody Map<String, String> payload) {
        String contactUsername = payload.get("username");
        var currentUserOpt = userRepository.findByUsername(username);
        var contactUserOpt = userRepository.findByUsername(contactUsername);

        if (currentUserOpt.isPresent() && contactUserOpt.isPresent()) {
            currentUserOpt.get().addContact(contactUsername);
            userRepository.save(currentUserOpt.get());
            return new ResponseEntity<>(HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * --- NOVO ENDPOINT ---
     * Remove um utilizador da lista de contactos de outro.
     */
    @DeleteMapping("/{username}/contacts/{contactUsername}")
    public ResponseEntity<Void> removeContact(@PathVariable String username, @PathVariable String contactUsername) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.removeContact(contactUsername);
                    userRepository.save(user);
                    return new ResponseEntity<Void>(HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * --- NOVO ENDPOINT ---
     * Retorna todos os utilizadores que NÃO estão na lista de contactos do utilizador atual.
     */
    @GetMapping("/{username}/discover")
    public ResponseEntity<List<User>> getDiscoverableUsers(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(currentUser -> {
                    List<User> discoverable = userRepository.findAll().stream()
                            .filter(otherUser -> !otherUser.getUsername().equals(username) && !currentUser.getContacts().contains(otherUser.getUsername()))
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(discoverable);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

}