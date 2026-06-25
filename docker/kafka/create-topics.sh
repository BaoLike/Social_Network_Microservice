#!/bin/sh
# Tạo Kafka topics cho NexaServer (chạy một lần sau khi broker sẵn sàng)
# Post đăng bài + AI moderation: HTTP sync — không có topic moderation
set -eu

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"

TOPICS="
user.registered
user.email.verify.requested
user.email.verified
post.liked
post.commented
story.created
notification.push.requested
notification.interaction.created
notification.push.requested.DLT
notification.interaction.created.DLT
user.registered.DLT
"

echo "Creating topics on ${BOOTSTRAP} (partitions=${PARTITIONS}, replication=${REPLICATION})..."

for topic in $TOPICS; do
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}" \
    --config retention.ms=604800000 \
    --config compression.type=lz4
  echo "  OK: ${topic}"
done

echo "Done. Listing topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list
