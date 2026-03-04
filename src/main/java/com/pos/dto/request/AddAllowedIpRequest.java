package com.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddAllowedIpRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "IP address is required")
    private String ipAddress;
}
