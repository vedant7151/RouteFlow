package com.routeflow.service;

import com.routeflow.domain.User;
import com.routeflow.domain.enums.Role;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> findAll(Role role) {
        return role != null ? userRepository.findByRole(role) : userRepository.findAll();
    }

    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** Soft-disable: blocks future logins without deleting the account or its order/route history. */
    public void setEnabled(String id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        userRepository.save(user);
    }
}
