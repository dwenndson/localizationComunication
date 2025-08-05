package br.ifce.ppd.diego.config;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.repository.UserRepository;
import br.ifce.ppd.diego.service.NotificationService;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSockerEventListenerComponent implements ApplicationListener<SessionDisconnectEvent> {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public WebSockerEventListenerComponent(UserRepository userRepository,
                                           NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Principal principal = event.getUser();

        String username = principal.getName();

        userRepository.findByUsername(username).ifPresent(user -> {
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);
            System.out.println("### Utilizador desconectado e status atualizado para OFFLINE: " + username);
            notificationService.notifyContactsOfUpdate(username);
        });
    }
}