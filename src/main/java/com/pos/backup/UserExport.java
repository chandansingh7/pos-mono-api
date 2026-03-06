package com.pos.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExport {
    private Long id;
    private String username;
    private String email;
    private String password; // hash only
    private String role;
    private boolean active;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String deliveryAddress;
}
