package com.appsdeveloperblog.api.users.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UsersServiceException extends RuntimeException {

    private final HttpStatus status;

    public UsersServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
