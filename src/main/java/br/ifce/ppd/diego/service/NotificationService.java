package br.ifce.ppd.diego.service;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public NotificationService(SimpMessagingTemplate messagingTemplate, UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    /**
     * Envia uma notificação para um utilizador específico para que ele atualize a sua lista de contactos.
     */
    public void notifyUserToUpdateContacts(String username) {
        String destination = "/user/" + username + "/queue/notifications";
        // A mensagem pode ser simples, o importante é que ela chegue.
        messagingTemplate.convertAndSend(destination, "UPDATE");
        System.out.println("--> Notificação de atualização enviada para: " + username);
    }

    /**
     * Encontra todos os utilizadores que têm 'changedUser' como contacto e notifica-os para atualizarem.
     * @param changedUser O utilizador cujo estado foi alterado.
     */
    public void notifyContactsOfUpdate(String changedUser) {
        userRepository.findAll().stream()
                .filter(user -> user.getContacts().contains(changedUser))
                .forEach(user -> notifyUserToUpdateContacts(user.getUsername()));
    }

    public void notifyAllOnlineUsersOfUpdate(String userWhoLoggedIn) {
        userRepository.findAll().stream()
                .filter(user -> !user.getUsername().equals(userWhoLoggedIn) && user.getStatus() == UserStatus.ONLINE)
                .forEach(user -> notifyUserToUpdateContacts(user.getUsername()));
    }
}
