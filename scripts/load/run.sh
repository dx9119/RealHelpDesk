#!/usr/bin/env bash
# Запуск нагрузочного user journey (k6): сидирование пула пользователей + прогон.
#
# Требует поднятый стек с load-оверлеем (make up-load) — включённый
# TRUST_PROXY_HEADERS даёт каждой VU свой bucket рейт-лимитов.
#
# Параметры (окружение):
#   POOL_SIZE   — сколько пользователей сидировать (по умолчанию 8, максимум 10:
#                 лимит регистрации 10/300с на один IP; сидирование шлёт
#                 уникальный X-Forwarded-For на пользователя, чтобы прошлые
#                 прогоны не съедали общий bucket)
#   RUN_ID      — метка пула в email (по умолчанию unixtime; повторный запуск
#                 с тем же RUN_ID переиспользует уже сидированных пользователей)
#   TARGET_VUS  — число параллельных VU k6 (по умолчанию 10)
#   DURATION    — длительность прогона, например 5m / 30s (по умолчанию 5m)
#   THINK_TIME  — пауза между итерациями, сек (по умолчанию 1)
#   RESET_LIMITS=1 — перезапустить app перед прогоном: in-memory счётчики
#                 рейт-лимитов обнуляются (окно логина 10/300с от предыдущих
#                 прогонов иначе может отказать первым же логинам)
set -euo pipefail
cd "$(dirname "$0")/../.."

POOL_SIZE=${POOL_SIZE:-8}
RUN_ID=${RUN_ID:-$(date +%s)}
TARGET_VUS=${TARGET_VUS:-10}
DURATION=${DURATION:-5m}
PASSWORD=${PASSWORD:-Password123!}
BASE=${BASE:-https://localhost:8443}
COMPOSE="docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml -f infrastructure/docker-compose.load.yaml"

if [ "$POOL_SIZE" -gt 10 ]; then
  echo "POOL_SIZE=$POOL_SIZE > 10: лимит регистрации 10/300с на IP, пул не засеивается за один проход." >&2
  echo "Уменьшите POOL_SIZE или добавьте sleep 300 между партиями в run.sh." >&2
  exit 1
fi

if [ "${RESET_LIMITS:-0}" = "1" ]; then
  echo "── RESET_LIMITS=1: перезапуск app (обнуление счётчиков рейт-лимитов)"
  $COMPOSE restart app
fi

echo "── Проверка стека ($BASE/actuator/health)"
if ! timeout 120 bash -c 'until curl -skf "$0/actuator/health" >/dev/null; do sleep 3; done' "$BASE"; then
  echo "Стек не отвечает. Поднимите: make up-load" >&2
  exit 1
fi

echo "── Сидирование пула: load-pool-$RUN_ID-p{1..$POOL_SIZE}@load.test"
for i in $(seq 1 "$POOL_SIZE"); do
  email="load-pool-${RUN_ID}-p${i}@load.test"
  # уникальный XFF на пользователя: сидирование не делит общий bucket регистрации
  code=$(curl -sk -o /dev/null -w '%{http_code}' \
    -H 'X-Requested-With: XMLHttpRequest' -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: 10.99.0.${i}" \
    -X POST "$BASE/api/v1/auth/register" \
    -d "{\"firstName\":\"Load\",\"lastName\":\"Pool${i}\",\"email\":\"$email\",\"password\":\"$PASSWORD\"}")
  echo "  register p$i -> $code"
  if [ "$code" != "201" ] && [ "$code" != "409" ]; then
    echo "Неожиданный код $code (ожидался 201, повторная регистрация — 409)." >&2
    echo "429 — исчерпан лимит регистрации: RESET_LIMITS=1 make load-smoke (сброс счётчиков)." >&2
    exit 1
  fi
done

echo "── Верификация email пула (psql)"
$COMPOSE exec -T postgres psql -U "${POSTGRES_USER:-user}" -d "${POSTGRES_DB:-desk}" \
  -c "update users set is_email_verified=true where email like 'load-pool-${RUN_ID}-%@load.test';"

echo "── k6: user journey (VU=$TARGET_VUS, duration=$DURATION, pool=$POOL_SIZE, run=$RUN_ID)"
$COMPOSE run --rm \
  -e RUN_ID="$RUN_ID" \
  -e POOL_SIZE="$POOL_SIZE" \
  -e TARGET_VUS="$TARGET_VUS" \
  -e DURATION="$DURATION" \
  k6
