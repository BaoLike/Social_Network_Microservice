package com.story.ig.payload.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoryResponse {
    private String mdeiaUrl;
    private String firstName;
    private String lastName;
    private String userName;
    private String avatar;
}
