package br.ifce.ppd.diego.locationchat.dto;

import lombok.Data;

@Data
public class MessageDTO {
    private String sender;
    private String recipient;
    private String content;
}
