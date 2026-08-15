package com.appsdeveloperblog.api.users.service;

import com.appsdeveloperblog.api.users.shared.UserDto;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UsersService extends UserDetailsService {
    UserDto createUser(UserDto user);
    List<UserDto> getUsers(int page, int limit);
    UserDto getUserById(String userId);
}
