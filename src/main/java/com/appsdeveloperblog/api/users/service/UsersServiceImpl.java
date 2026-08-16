package com.appsdeveloperblog.api.users.service;

import com.appsdeveloperblog.api.users.exceptions.UsersServiceException;
import com.appsdeveloperblog.api.users.io.UserEntity;
import com.appsdeveloperblog.api.users.io.UsersRepository;
import com.appsdeveloperblog.api.users.shared.UserDto;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UsersRepository usersRepository;
    private final ModelMapper modelMapper;

    @Override
    public UserDto createUser(UserDto user) {
        if (usersRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new UsersServiceException(HttpStatus.CONFLICT, "User with this email already exists");
        }

        UserEntity userEntity = modelMapper.map(user, UserEntity.class);
        userEntity.setUserId(UUID.randomUUID().toString());

        return modelMapper.map(usersRepository.save(userEntity), UserDto.class);
    }

    @Override
    public UserDto getUserById(String userId) {
        return modelMapper.map(findUser(userId), UserDto.class);
    }

    @Override
    public List<UserDto> getUsers(int page, int limit) {
        if (page > 0) {
            page -= 1;
        }
        List<UserEntity> users = usersRepository.findAll(PageRequest.of(page, limit)).getContent();
        return modelMapper.map(users, new TypeToken<List<UserDto>>() {}.getType());
    }

    @Override
    public UserDto updateUser(String userId, UserDto user) {
        UserEntity existing = findUser(userId);

        usersRepository.findByEmail(user.getEmail())
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new UsersServiceException(HttpStatus.CONFLICT, "User with this email already exists");
                });

        existing.setFirstName(user.getFirstName());
        existing.setLastName(user.getLastName());
        existing.setEmail(user.getEmail());

        return modelMapper.map(usersRepository.save(existing), UserDto.class);
    }

    @Override
    public void deleteUser(String userId) {
        usersRepository.delete(findUser(userId));
    }

    private UserEntity findUser(String userId) {
        return usersRepository.findByUserId(userId)
                .orElseThrow(() -> new UsersServiceException(HttpStatus.NOT_FOUND, "User with ID " + userId + " not found"));
    }
}
