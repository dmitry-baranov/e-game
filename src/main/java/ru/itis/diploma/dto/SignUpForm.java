package ru.itis.diploma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SignUpForm {
    @Size(min = 4, max =  100)
    @NotBlank
    private String fullName;

    @NotBlank
    @ValidPassword(message = "{validation.password.error-message}")
    private String password;

    @Email
    @NotBlank
    private String email;
}
