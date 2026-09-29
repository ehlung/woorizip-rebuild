#!/bin/bash
# LocalStack 준비 완료 시 로컬 개발용 S3 버킷과 SQS 큐를 생성한다.
set -euo pipefail

BUCKET="${S3_BUCKET:-woorizip-media-local}"

awslocal s3 mb "s3://${BUCKET}"

for queue in analysis-jobs analysis-results; do
  awslocal sqs create-queue --queue-name "${queue}-dlq" > /dev/null
  dlq_url=$(awslocal sqs get-queue-url --queue-name "${queue}-dlq" --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "${dlq_url}" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)

  awslocal sqs create-queue --queue-name "${queue}" \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" > /dev/null
done

echo "LocalStack 초기화 완료: s3://${BUCKET}, analysis-jobs, analysis-results (+ DLQ)"
