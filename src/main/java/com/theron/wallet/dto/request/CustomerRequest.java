package com.theron.wallet.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "CPF or CNPJ is required")
    @Pattern(regexp = "\\d{11}|\\d{14}", message = "CPF must have 11 digits or CNPJ must have 14 digits")
    private String cpfCnpj;

    @Pattern(regexp = "\\d{10,11}", message = "Phone must have 10 or 11 digits")
    private String phone;

    @Pattern(regexp = "\\d{10,11}", message = "Mobile phone must have 10 or 11 digits")
    private String mobilePhone;
}
