package com.common_library.common.kafka;

public final class EventTypes {

    private EventTypes() {}

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_EMAIL_VERIFY_REQUESTED = "USER_EMAIL_VERIFY_REQUESTED";
    public static final String USER_EMAIL_VERIFIED = "USER_EMAIL_VERIFIED";

    public static final String POST_LIKED = "POST_LIKED";
    public static final String POST_COMMENTED = "POST_COMMENTED";
    public static final String NOTIFICATION_INTERACTION_CREATED = "NOTIFICATION_INTERACTION_CREATED";

    public static final String STORY_CREATED = "STORY_CREATED";
    public static final String NOTIFICATION_PUSH_REQUESTED = "NOTIFICATION_PUSH_REQUESTED";
}
