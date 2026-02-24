package com.namnd.springjwt.dto;

import lombok.Data;

@Data
public class ResetPasswordDto {

    private String token;

    private String newPassword;
}
