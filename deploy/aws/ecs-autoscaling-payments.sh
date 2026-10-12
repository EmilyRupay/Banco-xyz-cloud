#!/usr/bin/env bash
# Escalado horizontal automatico del servicio ECS payments-service: entre 2 y 6 tareas segun CPU (objetivo 60%).
set -euo pipefail
CLUSTER=${CLUSTER:-banco-xyz}
SERVICE=${SERVICE:-payments-service}
aws application-autoscaling register-scalable-target \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id "service/$CLUSTER/$SERVICE" --min-capacity 2 --max-capacity 6
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs --scalable-dimension ecs:service:DesiredCount \
  --resource-id "service/$CLUSTER/$SERVICE" --policy-name cpu60 --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{"TargetValue":60.0,"PredefinedMetricSpecification":{"PredefinedMetricType":"ECSServiceAverageCPUUtilization"},"ScaleInCooldown":120,"ScaleOutCooldown":60}'
