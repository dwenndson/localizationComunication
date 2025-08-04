package br.ifce.ppd.diego.locationchat;

public enum UserStatus {
    ONLINE,
    OFFLINE
}

// Enum para representar o tipo de comunicação possível com um contacto.
enum CommunicationType {
    SYNC,  // Síncrona (online e dentro do raio)
    ASYNC  // Assíncrona (offline ou fora do raio)
}

