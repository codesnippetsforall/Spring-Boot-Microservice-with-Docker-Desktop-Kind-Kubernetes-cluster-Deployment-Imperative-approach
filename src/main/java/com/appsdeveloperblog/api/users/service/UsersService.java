package com.appsdeveloperblog.api.users.service;

import com.appsdeveloperblog.api.users.shared.UserDto;

import java.util.List;

public interface UsersService {
    UserDto createUser(UserDto user);
    UserDto getUserById(String userId);
    List<UserDto> getUsers(int page, int limit);
    UserDto updateUser(String userId, UserDto user);
    void deleteUser(String userId);
}
