package com.appsdeveloperblog.api.users.ui.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDetailsRequestModel {

    @NotBlank
    @Size(min = 2, message = "First name must not be less than 2 characters")
    private String firstName;

    @NotBlank
    @Size(min = 2, message = "Last name must not be less than 2 characters")
    private String lastName;

    @NotBlank
    @Email
    private String email;
}
