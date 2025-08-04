package br.ifce.ppd.diego.repository;


import br.ifce.ppd.diego.locationchat.identity.User;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {

    private final Map<String, User> users = new ConcurrentHashMap<>();

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public Collection<User> findAll() {
        return users.values();
    }

    public boolean save(User user) {
        return users.put(user.getUsername(), user) == null;
    }
}