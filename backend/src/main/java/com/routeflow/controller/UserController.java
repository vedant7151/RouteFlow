package com.routeflow.controller;

import com.routeflow.domain.enums.Role;
import com.routeflow.dto.user.UserResponse;
import com.routeflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User listing + enable/disable for the dispatcher console (e.g. picking a driver to assign to
 * a vehicle, or deactivating one). Account creation stays on POST /api/auth/register.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return userService.findAll(role).stream().map(UserResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        userService.setEnabled(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String id) {
        userService.setEnabled(id, true);
        return ResponseEntity.noContent().build();
    }
}
