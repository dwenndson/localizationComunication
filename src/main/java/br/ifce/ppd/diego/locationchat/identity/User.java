package br.ifce.ppd.diego.locationchat.identity;

import br.ifce.ppd.diego.locationchat.UserStatus;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@Getter
public class User {

    private String username;
    private UserStatus status;
    private double latitude;
    private double longitude;
    private double communicationRadiusKm;
    private Set<String> contacts = new HashSet<>();

    public User(String username, double latitude, double longitude, double communicationRadiusKm) {
        this.username = username;
        this.latitude = latitude;
        this.longitude = longitude;
        this.communicationRadiusKm = communicationRadiusKm;
        this.status = UserStatus.ONLINE;
    }

    public void addContact(String username) {
        this.contacts.add(username);
    }

    public void removeContact(String username) {
        this.contacts.remove(username);
    }
}
