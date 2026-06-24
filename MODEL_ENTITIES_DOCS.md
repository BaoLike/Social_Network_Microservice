# Social Network Microservice - Model/Entity Docs

## Identity Service (`identity/identity`)

### Base Audit (shared from common library)

#### `BaseAuditEntity` (MappedSuperclass)
- Purpose: common audit metadata for JPA entities.
- Storage type: relational (inherited columns).

| Field | Type | Description |
|---|---|---|
| `createdAt` | `Instant` | Timestamp created by Spring Data auditing (`@CreatedDate`). |
| `updatedAt` | `Instant` | Timestamp last updated (`@LastModifiedDate`). |
| `version` | `Long` | Optimistic locking version (`@Version`). |

---

### `User` (`@Entity`, table `users`)
- Extends `BaseAuditEntity`.
- Represents account identity information.

| Field | Type | Description |
|---|---|---|
| `userId` | `String` | Primary key (`UUID`). |
| `userName` | `String` | Unique username. |
| `password` | `String` | Encrypted password. |
| `email` | `String` | Unique email. |
| `emailVerified` | `Boolean` | Email verification status (default false). |
| `userStatus` | `UserStatus` | User state enum, stored as string enum. |
| `tokens` | `List<RefreshToken>` | One-to-many refresh tokens. |
| `emailVerifyTokens` | `List<EmailVerifyToken>` | One-to-many email verification tokens. |

### `RefreshToken` (`@Entity`, table `refresh_token`)
- Stores refresh tokens per user.

| Field | Type | Description |
|---|---|---|
| `tokenId` | `Long` | Primary key (auto increment). |
| `refreshToken` | `String` | Unique refresh token string. |
| `users` | `User` | Owner user (`ManyToOne`). |

### `EmailVerifyToken` (`@Entity`)
- Stores email verification token and expiration.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Primary key (`UUID`). |
| `emailVerifyToken` | `String` | Unique verification token. |
| `users` | `User` | Owner user (`ManyToOne`). |
| `expiredAt` | `Instant` | Token expiration time. |

---

## Profile Service (`profile/profile`)

### `UserProfile` (`@Node("user_profile")`, Neo4j)
- Main user profile node.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Internal Neo4j id (`UUIDStringGenerator`). |
| `userId` | `String` | Identity service user id (business key). |
| `userName` | `String` | Username. |
| `avatar` | `String` | Avatar URL. |
| `firstName` | `String` | First name. |
| `lastName` | `String` | Last name. |
| `gender` | `String` | Gender text. |
| `dob` | `LocalDate` | Date of birth. |
| `address` | `String` | Address. |
| `phone` | `String` | Phone number. |

### `FollowRelationship` (`@RelationshipProperties`, Neo4j)
- Relationship model for follow graph.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Relationship id (generated). |
| `createdAt` | `LocalDateTime` | Follow creation timestamp. |
| `notificationEnabled` | `Boolean` | Notification preference for follow relation. |
| `target` | `UserProfile` | Target node (`@TargetNode`). |
| `following` | `Set<FollowRelationship>` | Nested outgoing relation metadata. |

---

## Chat Service (`chat_service/chat_service`)

### `Conversation` (`@Document("conversation")`, MongoDB)
- Chat room/thread metadata.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Mongo document id. |
| `type` | `String` | Conversation type (`GROUP`/`DIRECT`). |
| `participantHash` | `String` | Unique participant hash for direct chats. |
| `participants` | `List<ParticipantInfo>` | Participant info list. |
| `createdDate` | `Instant` | Created timestamp. |
| `modifiedDate` | `Instant` | Last modified timestamp. |

### `ChatMessage` (`@Document("chat_message")`, MongoDB)
- Message document per conversation.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Mongo document id. |
| `conversationId` | `String` | Indexed reference to conversation id. |
| `message` | `String` | Message text body. |
| `sender` | `ParticipantInfo` | Sender metadata snapshot. |
| `createdDate` | `Instant` | Indexed creation timestamp. |

### `WebSocketSession` (`@Document("web_socket_session")`, MongoDB)
- Tracks active websocket sessions.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Mongo document id. |
| `socketSessionId` | `String` | Socket session identifier. |
| `userId` | `String` | Linked user id. |
| `createdAt` | `Instant` | Session creation timestamp. |

### `ParticipantInfo` (embedded model)
- Non-persistent standalone class used inside `Conversation` and `ChatMessage`.

| Field | Type | Description |
|---|---|---|
| `userId` | `String` | Participant user id. |
| `userName` | `String` | Participant username. |
| `avatar` | `String` | Participant avatar URL. |

---

## Post Service (`PostService`)

### `Post` (`@Entity`, table `post`)
- Main post entity for feed/profile.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Post id (string key). |
| `userId` | `String` | Post owner user id. |
| `firstName` | `String` | Owner first name snapshot. |
| `avatarUrl` | `String` | Owner avatar snapshot. |
| `lastName` | `String` | Owner last name snapshot. |
| `userName` | `String` | Owner username snapshot. |
| `description` | `String` | Post text description. |
| `liked` | `Long` | Like count. |
| `createAt` | `LocalDateTime` | Created timestamp. |
| `urlMedia` | `String` | Uploaded media URL. |
| `commentList` | `List<Comment>` | One-to-many comments. |

### `Comment` (`@Entity`, table `comment`)
- Comment entity linked to a post.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key (auto). |
| `userName` | `String` | Commenter username snapshot. |
| `firstName` | `String` | Commenter first name snapshot. |
| `lastName` | `String` | Commenter last name snapshot. |
| `avatarUrl` | `String` | Commenter avatar snapshot. |
| `comment` | `String` | Comment body. |
| `commentAt` | `LocalDateTime` | Comment creation time. |
| `liked` | `Long` | Comment like count. |
| `post` | `Post` | Many-to-one link to parent post. |

### `PostLike` (`@Entity`, table `post_like`)
- Tracks user likes per post.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key (identity). |
| `postId` | `String` | Liked post id. |
| `userId` | `String` | User who liked. |
| `createdAt` | `LocalDateTime` | Like action timestamp. |

> Constraint: unique pair (`post_id`, `user_id`) to prevent duplicate like by same user.

---

## Story Service (`story_service/story_service`)

### `Story` (`@Document("stories")`, MongoDB)
- Story item model.
- Extends `BaseAuditEntity` (shared audit fields are inherited conceptually, but persistence behavior depends on module setup).

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Story id (`UUID`). |
| `userId` | `String` | Story owner user id. |
| `urlMedia` | `String` | Story media URL. |
| `avatar` | `String` | Owner avatar snapshot. |
| `firstName` | `String` | Owner first name snapshot. |
| `lastName` | `String` | Owner last name snapshot. |
| `liked` | `Long` | Like count. |
| `description` | `String` | Story description/text. |

### `UserStories` (`@Document("UserStories")`, MongoDB)
- Aggregate stories per user for retrieval.

| Field | Type | Description |
|---|---|---|
| `userId` | `String` | Primary id/key (mapped to `user_id`). |
| `listStories` | `List<Story>` | Embedded list of stories. |
| `firstName` | `String` | User first name snapshot. |
| `lastName` | `String` | User last name snapshot. |
| `avatar` | `String` | User avatar snapshot. |

---

## Notification Service (`notification_service/notification_service`)

### `UserDevices` (`@Document`, MongoDB)
- Maps user id to registered device token for push notification.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Mongo document id. |
| `userId` | `String` | User owning the device token. |
| `deviceToken` | `String` | FCM device token. |

