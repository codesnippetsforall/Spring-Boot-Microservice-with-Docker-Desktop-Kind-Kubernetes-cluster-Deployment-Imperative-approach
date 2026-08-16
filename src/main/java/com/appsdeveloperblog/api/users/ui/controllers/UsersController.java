package com.appsdeveloperblog.api.users.ui.controllers;

import com.appsdeveloperblog.api.users.service.UsersService;
import com.appsdeveloperblog.api.users.shared.UserDto;
import com.appsdeveloperblog.api.users.ui.request.UserDetailsRequestModel;
import com.appsdeveloperblog.api.users.ui.response.UserRest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final UsersService usersService;
    private final ModelMapper modelMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRest createUser(@RequestBody @Valid UserDetailsRequestModel request) {
        UserDto created = usersService.createUser(modelMapper.map(request, UserDto.class));
        return withPodName(modelMapper.map(created, UserRest.class));
    }

    @GetMapping("/{userId}")
    public UserRest getUser(@PathVariable String userId) {
        return withPodName(modelMapper.map(usersService.getUserById(userId), UserRest.class));
    }

    @GetMapping
    public List<UserRest> getUsers(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int limit) {
        List<UserRest> users = modelMapper.map(
                usersService.getUsers(page, limit),
                new TypeToken<List<UserRest>>() {}.getType());
        users.forEach(this::withPodName);
        return users;
    }

    @PutMapping("/{userId}")
    public UserRest updateUser(@PathVariable String userId,
                               @RequestBody @Valid UserDetailsRequestModel request) {
        UserDto updated = usersService.updateUser(userId, modelMapper.map(request, UserDto.class));
        return withPodName(modelMapper.map(updated, UserRest.class));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String userId) {
        usersService.deleteUser(userId);
    }

    private UserRest withPodName(UserRest user) {
        String hostname = System.getenv("HOSTNAME");
        user.setPodName(hostname == null || hostname.isBlank() ? "local" : hostname);
        return user;
    }
}
