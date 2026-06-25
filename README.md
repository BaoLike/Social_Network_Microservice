# Social_Network_Microservice

Microservice backend cho ứng dụng mạng xã hội NexaServer (Spring Boot).

## Services

| Service | Port (internal) | Database |
|---------|-----------------|----------|
| api-gateway | 8888 | — |
| identity | 8080 | PostgreSQL |
| profile | 8081 | Neo4j |
| notification | 8082 | MongoDB |
| chat | 8083 + socket 8099 | MongoDB |
| story | — | MongoDB |
| PostService | — | PostgreSQL |
| call | 8088 + socket 8100 | MongoDB |

## Kafka — Event-Driven Architecture

Thiết kế Apache Kafka cho đồ án: user lifecycle, push notification, tương tác post (like/comment).

**Ngoại lệ:** đăng post + AI kiểm duyệt + xác nhận trách nhiệm user → **HTTP sync**, không qua Kafka.

📄 **Tài liệu:** [`../docs/kafka/README.md`](../docs/kafka/README.md)

```bash
docker compose up -d kafka kafka-init kafka-ui
# UI: http://localhost:8090
```

Script topic: [`docker/kafka/create-topics.sh`](docker/kafka/create-topics.sh)
