package com.appsdeveloperblog.api.users.shared;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto {
    private long id;
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
}
