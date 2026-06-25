package com.common_library.common.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_EMAIL_VERIFY_REQUESTED = "user.email.verify.requested";
    public static final String USER_EMAIL_VERIFIED = "user.email.verified";

    public static final String POST_LIKED = "post.liked";
    public static final String POST_COMMENTED = "post.commented";
    public static final String NOTIFICATION_INTERACTION_CREATED = "notification.interaction.created";

    public static final String STORY_CREATED = "story.created";
    public static final String NOTIFICATION_PUSH_REQUESTED = "notification.push.requested";
}
