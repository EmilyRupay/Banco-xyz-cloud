#!/bin/bash
# =====================================================================================
# User Data para una instancia EC2 (Amazon Linux 2023, t3.xlarge o superior: 4 vCPU / 16 GB).
# Instala Docker + Compose, clona el repositorio y levanta el sistema completo.
# Uso: pegar este script en "Advanced details > User data" al lanzar la instancia.
# =====================================================================================
set -euxo pipefail

REPO_URL="https://github.com/EmilyRupay/Banco-xyz-cloud.git"
APP_DIR=/opt/banco-xyz

dnf update -y
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose v2 (plugin) y Buildx
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
     -o /usr/local/lib/docker/cli-plugins/docker-compose
curl -sSL "https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-$( [ "$(uname -m)" = "aarch64" ] && echo arm64 || echo amd64)" \
     -o /usr/local/lib/docker/cli-plugins/docker-buildx
chmod +x /usr/local/lib/docker/cli-plugins/*

git clone "$REPO_URL" "$APP_DIR"
cd "$APP_DIR"

# Secretos: en produccion se leen desde AWS Secrets Manager (requiere un rol IAM con secretsmanager:GetSecretValue)
if aws secretsmanager get-secret-value --secret-id banco-xyz/prod --query SecretString --output text > /tmp/s.json 2>/dev/null; then
  python3 -c "import json;[print(f'{k}={v}') for k,v in json.load(open('/tmp/s.json')).items()]" > .env
  rm -f /tmp/s.json
else
  cp .env.example .env
fi

docker compose up --build -d
