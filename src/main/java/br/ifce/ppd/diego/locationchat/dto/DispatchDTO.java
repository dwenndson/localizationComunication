package br.ifce.ppd.diego.locationchat.dto;

import br.ifce.ppd.diego.locationchat.identity.CommunicationType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DispatchDTO {
    private MessageDTO message;
    private CommunicationType communicationType;
}

