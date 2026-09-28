#!/usr/bin/env bash
# E2E: рейт-лимиты.
#
# Проверяет троттлинг по IP + эндпоинту (ответ 429 + Retry-After).
# Лимиты действуют одинаково при включенной и выключенной капче:
#   GET  /captcha                      30 / 60 сек
#   POST /auth/register                10 / 5 мин
#   POST /auth/login                   10 / 5 мин
#   POST /user/passwd-reset/request     3 / 10 мин
#
# Счетчики живут в памяти приложения: если лимит уже исчерпан прошлым
# запуском, скрипт об этом скажет — перезапустите приложение
# (docker compose restart app) и повторите. Пока окно /captcha не истекло
# (60 сек), проверка капчи не сойдется. Капча должна быть выключена
# (по умолчанию).
set -u

BASE=${BASE:-https://localhost:8443/api/v1}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
BODY="$TMP/body.txt"
HEADERS="$TMP/headers.txt"

FAIL=0
step() { echo; echo "── $1"; }
check() {
  if [ "$1" = "$2" ]; then
    echo "  OK   $3"
  else
    echo "  FAIL $3 (факт: $1, ожидалось: $2)"
    FAIL=1
  fi
}
check_ge() {
  if [ "$1" -ge "$2" ]; then
    echo "  OK   $3"
  else
    echo "  FAIL $3 (факт: $1, ожидалось >= $2)"
    FAIL=1
  fi
}

echo "═══ Рейт-лимиты ═══"

step "GET /captcha: 31 запрос подряд (лимит 30/60с)"
first=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/captcha?capId=rl-probe")
if [ "$first" != "200" ]; then
  echo "  FAIL окно /captcha уже исчерпано (http=$first)"
  echo "  подсказка: docker compose restart app && bash scripts/e2e-ratelimit.sh"
  exit 1
fi
ok=1; r429=0
for i in $(seq 1 30); do
  c=$(curl -sk -o /dev/null -w "%{http_code}" "$BASE/captcha?capId=rl$i")
  [ "$c" = "200" ] && ok=$((ok + 1))
  [ "$c" = "429" ] && r429=$((r429 + 1))
done
check "$((ok + r429))" "31" "все 31 запрос обработаны"
check_ge "$r429" "1" "после лимита пришел 429"

step "Ответ 429 несёт Retry-After и JSON"
status=$(curl -sk -D "$HEADERS" -o "$BODY" -w "%{http_code}" "$BASE/captcha?capId=rl-after")
check "$status" "429" "статус"
check "$(grep -ci '^retry-after:' "$HEADERS")" "1" "заголовок Retry-After присутствует"
grep -qi 'Слишком много запросов' "$BODY"
check "$?" "0" "тело ответа с сообщением об ошибке"

step "POST /auth/register: 11 запросов (лимит 10/5мин)"
r429=0; last=""
for i in $(seq 1 11); do
  c=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"firstName":"Rate","lastName":"Limiter","email":"ratelimit@test.local","password":"Password123!"}')
  last=$c
  [ "$c" = "429" ] && r429=$((r429 + 1))
done
check_ge "$r429" "1" "после лимита пришел 429"
check "$last" "429" "последний запрос заблокирован"

step "POST /auth/login: 11 попыток с неверным паролем (лимит 10/5мин)"
last=""
for i in $(seq 1 11); do
  last=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"email":"nobody@test.local","password":"WrongPass123!"}')
done
check "$last" "429" "последняя попытка входа заблокирована"

step "POST /user/passwd-reset/request: 4 запроса (лимит 3/10мин)"
last=""
for i in $(seq 1 4); do
  last=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$BASE/user/passwd-reset/request" \
    -H 'Content-Type: application/json' -d '{"email":"nobody@test.local"}')
done
check "$last" "429" "последний запрос сброса пароля заблокирован"

step "Эндпоинт без @RateLimit не ограничен"
check "$(curl -sk -o /dev/null -w '%{http_code}' "$BASE/health-check")" "200" "health-check отвечает 200"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "═══ ИТОГ: все проверки прошли ═══"
else
  echo "═══ ИТОГ: есть проваленные проверки ═══"
fi
exit "$FAIL"
