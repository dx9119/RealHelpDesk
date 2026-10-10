#!/usr/bin/env bash
# E2E: права управления порталом.
#
# Проверяет правила доступа: участник портала = владелец или доверенный
# пользователь. Посторонний не читает участников и не переименовывает
# портал даже после того, как владелец сделал его публичным (isPublic);
# публичность открывает только чтение имени/описания (GET /portals/{id}).
# Смена isPublic доступна только владельцу.
#
# Требования: поднятый стек (make up), капча выключена
# (по умолчанию), docker для запросов к postgres. Регистрирует двух
# пользователей со временными email.
set -u

BASE=${BASE:-https://localhost:8443/api/v1}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
JAR_A="$TMP/owner.jar"
JAR_B="$TMP/guest.jar"
BODY="$TMP/body.txt"
PSQL="docker exec realhelpdesk_postgres psql -U user -d desk -tA -c"
RUN=$(date +%s)
OWNER_EMAIL="owner-$RUN@test.local"
GUEST_EMAIL="guest-$RUN@test.local"

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
code() { curl -sk -o "$BODY" -w "%{http_code}" "$@"; }

echo "═══ Права на портал ═══"

step "Регистрация владельца A и гостя B"
s=$(code -c "$JAR_A" -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Alpha\",\"lastName\":\"Owner\",\"email\":\"$OWNER_EMAIL\",\"password\":\"Password123!\"}")
if [ "$s" = "429" ]; then
  echo "  FAIL лимит регистрации исчерпан — make restart и повторите скрипт"
  exit 1
fi
check "$s" "201" "A зарегистрирован"
check "$(code -c "$JAR_B" -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Bravo\",\"lastName\":\"Guest\",\"email\":\"$GUEST_EMAIL\",\"password\":\"Password123!\"}")" \
  "201" "B зарегистрирован"
$PSQL "update users set is_email_verified=true;" >/dev/null

check "$(code -b "$JAR_A" -c "$JAR_A" -X POST "$BASE/portals" -H 'Content-Type: application/json' \
  -d '{"name":"public-portal","description":"открытый"}')" \
  "201" "A создал портал"
PORTAL=$($PSQL "select p.id from portals p join users u on u.id=p.owner_id where u.email='$OWNER_EMAIL';")
GUEST_ID=$($PSQL "select id from users where email='$GUEST_EMAIL';")
echo "  портал=$PORTAL, B id=$GUEST_ID"

check "$(code -b "$JAR_A" -X PUT "$BASE/portals/shared/$PORTAL/visibility" -H 'Content-Type: application/json' \
  -d '{"isPublic":true}')" \
  "204" "A сделал портал публичным"

echo
echo "═══ Посторонний B на публичном портале ═══"
check "$(code -b "$JAR_B" "$BASE/portals/shared/$PORTAL")" \
  "403" "B не читает участников (GET /portals/shared/{id})"
check "$(code -b "$JAR_B" -X PUT "$BASE/portals/$PORTAL" -H 'Content-Type: application/json' \
  -d '{"name":"hacked","description":"взломано"}')" \
  "403" "B не переименовывает портал (PUT /portals/{id})"
check "$(code -b "$JAR_B" "$BASE/portals/$PORTAL")" \
  "200" "B читает публичное имя/описание (GET /portals/{id})"

echo
echo "═══ B получает доступ ═══"
check "$(code -b "$JAR_A" -X PUT "$BASE/portals/shared/$PORTAL/users" -H 'Content-Type: application/json' \
  -d "{\"userIds\":[\"$GUEST_ID\"]}")" \
  "204" "A выдал B доступ"

check "$(code -b "$JAR_B" "$BASE/portals/shared/$PORTAL")" \
  "200" "B (доверенный) читает участников"
check "$(code -b "$JAR_B" -X PUT "$BASE/portals/$PORTAL" -H 'Content-Type: application/json' \
  -d '{"name":"public-portal-v2","description":"обновлено доверенным"}')" \
  "200" "B (доверенный) переименовывает портал"
check "$(code -b "$JAR_B" -X PUT "$BASE/portals/shared/$PORTAL/visibility" -H 'Content-Type: application/json' \
  -d '{"isPublic":false}')" \
  "403" "B не меняет isPublic — это только владелец"

echo
echo "═══ Владелец A ═══"
check "$(code -b "$JAR_A" "$BASE/portals/shared/$PORTAL")" \
  "200" "A читает участников"
check "$(code -b "$JAR_A" -X PUT "$BASE/portals/$PORTAL" -H 'Content-Type: application/json' \
  -d '{"name":"public-portal-final","description":"обновлено владельцем"}')" \
  "200" "A переименовывает портал"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "═══ ИТОГ: все проверки прошли ═══"
else
  echo "═══ ИТОГ: есть проваленные проверки ═══"
fi
exit "$FAIL"
