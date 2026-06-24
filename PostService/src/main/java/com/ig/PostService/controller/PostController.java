package com.ig.PostService.controller;

import com.ig.PostService.exception.AccountLockedException;
import com.ig.PostService.exception.PostViolationException;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.request.UpdatePostRequest;
import com.ig.PostService.payload.request.UserReuest;
import com.ig.PostService.payload.request.ViolationReportRequest;
import com.ig.PostService.payload.response.ApiResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class PostController {
    @Autowired
    private PostService postService;

    @PostMapping(value = "/create" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPost(@RequestPart("data") PostRequest request, @RequestPart("media") MultipartFile media){
        try {
            return ResponseEntity.ok().body(postService.CreateNewPost(request, media));
        } catch (AccountLockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse("403", e.getMessage()));
        } catch (PostViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse("400", e.getMessage()));
        }
    }

    @PostMapping("/violation")
    public ResponseEntity<?> reportViolation(@RequestBody ViolationReportRequest request,
                                             HttpServletRequest httpRequest) {
        try {
            postService.recordViolationAttempt(
                    httpRequest.getHeader("Authorization"),
                    request != null ? request.getReason() : null);
            return ResponseEntity.ok(new ApiResponse("200", "Đã ghi nhận vi phạm"));
        } catch (AccountLockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse("403", e.getMessage()));
        } catch (PostViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse("400", e.getMessage()));
        }
    }

    @GetMapping("/profile/{user-id}")
    public UserPostProfileResponse getPostInUserProfile(@PathVariable("user-id") String userId){
        return postService.GetPostInUserProfile(userId);
    }

    @DeleteMapping("/delete/{user-id}/{post-id}")
    public void deletePost(@PathVariable("user-id") String userId, @PathVariable("post-id") String postId){
        postService.DeletePost(userId, postId);
    }

    @PutMapping("/like/{post-id}")
    public void likePost(@PathVariable("post-id") String postId,
                         HttpServletRequest request){
        postService.LikePost(postId, request.getHeader("Authorization"));
    }

    @PutMapping("/unlike/{post-id}")
    public void unlikePost(@PathVariable("post-id") String postId,
                           HttpServletRequest request){
        postService.unlikePost(postId, request.getHeader("Authorization"));
    }

    @PutMapping("/comment/{post-id}")
    public ApiResponse commentPost(@PathVariable("post-id") String postId, @RequestBody CommentRequest request){
        postService.commentPost(postId, request);
        return new ApiResponse();
    }

    @PutMapping("/like/comment/{comment-id}")
    public void likeComment(@PathVariable("comment-id") Long commentId,
                            HttpServletRequest request) {
        postService.likeComment(commentId, request.getHeader("Authorization"));
    }

    @PutMapping("/unlike/comment/{comment-id}")
    public void unlikeComment(@PathVariable("comment-id") Long commentId,
                              HttpServletRequest request) {
        postService.unlikeComment(commentId, request.getHeader("Authorization"));
    }

    @PutMapping("/update/{post-id}")
    public ResponseEntity<?> updatePost(@PathVariable("post-id") String postId,
                                        @RequestBody UpdatePostRequest request,
                                        HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.updatePost(
                    postId, httpRequest.getHeader("Authorization"), request));
        } catch (AccountLockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse("403", e.getMessage()));
        } catch (PostViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse("400", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse("400", e.getMessage()));
        }
    }

    @PostMapping("/clear-cache")
    public void clearCache(){
        postService.clearCache();
    }


    @GetMapping("/get-post")
    public List<PostResponse> getPost(HttpServletRequest request){
        return postService.getPost(request);
    }
}
