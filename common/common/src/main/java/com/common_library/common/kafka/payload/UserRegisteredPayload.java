package com.common_library.common.kafka.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredPayload {
    private String userId;
    private String username;
    private String email;
    private String registeredAt;
    private String avatar;
    private String firstName;
    private String lastName;
    private String gender;
    private String dob;
    private String address;
    private String phone;
}
