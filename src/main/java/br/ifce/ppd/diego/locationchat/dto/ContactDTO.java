package br.ifce.ppd.diego.locationchat.dto;

import br.ifce.ppd.diego.locationchat.UserStatus;
import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
    private String username;
    private UserStatus status;
    private double distanceKm;
    private CommunicationType communicationType;
}
