package br.ifce.ppd.diego.controller;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.locationchat.dto.ContactDTO;
import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import br.ifce.ppd.diego.locationchat.identity.User;
import br.ifce.ppd.diego.repository.UserRepository;
import br.ifce.ppd.diego.service.KafkaAdminService;
import br.ifce.ppd.diego.utils.LocationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final KafkaAdminService kafkaAdminService;

    public UserController(UserRepository userRepository, KafkaAdminService kafkaAdminService) {
        this.userRepository = userRepository;
        this.kafkaAdminService = kafkaAdminService;
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

        return ResponseEntity.ok(user);
    }

    @PutMapping("/{username}/location")
    public ResponseEntity<Void> updateLocation(@PathVariable String username, @RequestBody Map<String, Double> location) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setLatitude(location.get("latitude"));
                    user.setLongitude(location.get("longitude"));
                    userRepository.save(user);
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

    @GetMapping("/{username}/contacts")
    public ResponseEntity<List<ContactDTO>> getContacts(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(currentUser -> {
                    List<ContactDTO> contacts = userRepository.findAll().stream()
                            .filter(otherUser -> !otherUser.getUsername().equals(currentUser.getUsername()))
                            .map(otherUser -> {
                                double distance = LocationUtils.calculateDistance(
                                        currentUser.getLatitude(), currentUser.getLongitude(),
                                        otherUser.getLatitude(), otherUser.getLongitude()
                                );

                                CommunicationType type = (otherUser.getStatus() == UserStatus.ONLINE && distance <= currentUser.getCommunicationRadiusKm())
                                        ? CommunicationType.SYNC
                                        : CommunicationType.ASYNC;

                                return new ContactDTO(otherUser.getUsername(), otherUser.getStatus(), distance, type);
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(contacts);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}