#!/usr/bin/env bash
# =====================================================================================
# Construye las imagenes y las publica en Amazon ECR (paso previo a ECS Fargate o a EC2 sin compilar).
# Uso: AWS_REGION=us-east-1 ./deploy/aws/ecr-push.sh [tag]
# Requiere: AWS CLI v2 configurado (aws configure) y Docker.
# =====================================================================================
set -euo pipefail
REGION=${AWS_REGION:-us-east-1}
TAG=${1:-$(git rev-parse --short HEAD)}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"
SERVICES="config-server eureka-server auth-server gateway customers-service accounts-service payments-service \
notification-service bff-web bff-mobile bff-atm batch-service"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

for s in $SERVICES; do
  aws ecr describe-repositories --repository-names "banco-xyz/$s" --region "$REGION" >/dev/null 2>&1 \
    || aws ecr create-repository --repository-name "banco-xyz/$s" --image-scanning-configuration scanOnPush=true \
         --region "$REGION" >/dev/null
  docker build -f "$s/Dockerfile" -t "$REGISTRY/banco-xyz/$s:$TAG" -t "$REGISTRY/banco-xyz/$s:latest" .
  docker push "$REGISTRY/banco-xyz/$s:$TAG"
  docker push "$REGISTRY/banco-xyz/$s:latest"
done
echo "Imagenes publicadas en $REGISTRY/banco-xyz/*:$TAG"
