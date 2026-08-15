package com.appsdeveloperblog.api.users.ui.controllers;

import com.appsdeveloperblog.api.users.service.UsersService;
import com.appsdeveloperblog.api.users.shared.UserDto;
import com.appsdeveloperblog.api.users.ui.request.UserDetailsRequestModel;
import com.appsdeveloperblog.api.users.ui.response.UserRest;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UsersController {

    private final UsersService usersService;

    @Autowired
    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PostMapping
    public UserRest createUser(@RequestBody @Valid UserDetailsRequestModel userDetails) throws Exception {
        ModelMapper modelMapper = new ModelMapper();
        UserDto userDto = new ModelMapper().map(userDetails, UserDto.class);

        UserDto createdUser = usersService.createUser(userDto);

        return withPodName(modelMapper.map(createdUser, UserRest.class));
    }

    @GetMapping("/{userId}")
    public UserRest getUser(@PathVariable String userId) {
        UserDto userDto = usersService.getUserById(userId); // Call the service layer method
        return withPodName(new ModelMapper().map(userDto, UserRest.class)); // Map UserDto to UserRest
    }


    @GetMapping
    public List<UserRest> getUsers(@RequestParam(value = "page", defaultValue = "0") int page,
                                   @RequestParam(value = "limit", defaultValue = "2") int limit) {
        List<UserDto> users = usersService.getUsers(page, limit);

        Type listType = new TypeToken<List<UserRest>>() {
        }.getType();

        List<UserRest> returnValue = new ModelMapper().map(users, listType);
        returnValue.forEach(this::withPodName);
        return returnValue;
    }

    private UserRest withPodName(UserRest user) {
        String hostname = System.getenv("HOSTNAME");
        user.setPodName(hostname == null || hostname.isBlank() ? "local" : hostname);
        return user;
    }
}
