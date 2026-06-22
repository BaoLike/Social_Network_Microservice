package com.ig.PostService.mapper;

import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.payload.response.PostResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperWhiteBoxTest {

    private final Mapper mapper = new Mapper();

    @Test
    void postResponseMapper_mapsFields_andMapsComments_andDefaultsLikedByUserFalse() {
        LocalDateTime now = LocalDateTime.now();

        Comment c = new Comment();
        c.setUserName("alice");
        c.setFirstName("Alice");
        c.setLastName("Nguyen");
        c.setAvatarUrl("avt");
        c.setComment("hi");
        c.setCommentAt(now);
        c.setLiked(2L);

        Post post = new Post();
        post.setId("p1");
        post.setUserId("u1");
        post.setFirstName("Bao");
        post.setLastName("Lai");
        post.setAvatarUrl("a1");
        post.setUserName("baolai");
        post.setDescription("desc");
        post.setLiked(10L);
        post.setCreateAt(now);
        post.setUrlMedia("url");
        post.setCommentList(List.of(c));

        PostResponse res = mapper.PostResponseMapper(post);

        assertThat(res.getId()).isEqualTo("p1");
        assertThat(res.getUserName()).isEqualTo("baolai");
        assertThat(res.getFirstName()).isEqualTo("Bao");
        assertThat(res.getLastName()).isEqualTo("Lai");
        assertThat(res.getAvatarUrl()).isEqualTo("a1");
        assertThat(res.getDescription()).isEqualTo("desc");
        assertThat(res.getLiked()).isEqualTo(10L);
        assertThat(res.getCreateAt()).isEqualTo(now);
        assertThat(res.getUrlMedia()).isEqualTo("url");
        assertThat(res.getLikedByUser()).isFalse();

        assertThat(res.getCommentList()).hasSize(1);
        assertThat(res.getCommentList().getFirst().getUserName()).isEqualTo("alice");
        assertThat(res.getCommentList().getFirst().getComment()).isEqualTo("hi");
        assertThat(res.getCommentList().getFirst().getLiked()).isEqualTo(2L);
    }
}

