package br.ifce.ppd.diego.locationchat.identity;

import br.ifce.ppd.diego.locationchat.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Getter
public class User {

    private String username;
    private UserStatus status;
    private double latitude;
    private double longitude;
    private double communicationRadiusKm;

    public User(String username, double latitude, double longitude, double communicationRadiusKm) {
        this.username = username;
        this.latitude = latitude;
        this.longitude = longitude;
        this.communicationRadiusKm = communicationRadiusKm;
        this.status = UserStatus.ONLINE;
    }
}
