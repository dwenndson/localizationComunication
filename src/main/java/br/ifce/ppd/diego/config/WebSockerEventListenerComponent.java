package br.ifce.ppd.diego.config;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.repository.UserRepository;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSockerEventListenerComponent implements ApplicationListener<SessionDisconnectEvent> {

    private final UserRepository userRepository;

    public WebSockerEventListenerComponent(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Principal principal = event.getUser();

        String username = principal.getName();

        userRepository.findByUsername(username).ifPresent(user -> {
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);
            System.out.println("### Utilizador desconectado e status atualizado para OFFLINE: " + username);
        });
    }
}