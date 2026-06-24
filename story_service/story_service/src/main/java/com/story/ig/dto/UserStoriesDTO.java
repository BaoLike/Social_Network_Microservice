package com.story.ig.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserStoriesDTO {
    private String code = "200";
    private String userId;
    private String firstName;
    private String lastName;
    private String avatar;
    private List<StoryDTO> stories;
}
