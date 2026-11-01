package com.example.ticketing.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ConfirmForm {

    @NotBlank(message = "Enter your name")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "Enter your email address")
    @Email(message = "Enter a valid email address")
    @Size(max = 320)
    private String email;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
