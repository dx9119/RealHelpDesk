#!/usr/bin/env bash
# E2E: in-app оповещения (long polling, прочтение, настройки, повторы).
#
# Проверяет: доверенный получает NEW_TICKET по long polling, пока владелец
# создаёт заявку; автор действия не получает уведомления о собственном
# действии; смена статуса уведомляет владельца; событие, отключённое в
# настройках, не приходит (пустой long polling отвечает 200 с content=[]
# по таймауту); чужое уведомление не читается (404); неизвестное событие
# в настройках — 400; без токена — 401.
#
# Повторы: без сохранённой строки настроек повтор включён на 30 минут;
# с интервалом 1 минута непрочитанная NEW_TICKET получает напоминание
# (проверка занимает до ~2.5 минут: интервал + период обхода раз в минуту),
# прочтение напоминания гасит всю группу; выключенный повтор напоминаний
# не даёт (проверка ~2 минуты ожидания).
#
# Требования: поднятый стек (docker compose up -d), капча выключена (по
# умолчанию), docker для запросов к postgres. Регистрирует двух
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
OWNER_EMAIL="notify-owner-$RUN@test.local"
GUEST_EMAIL="notify-guest-$RUN@test.local"

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
# непрочитанные из GET /notifications/unread-count
unread() { curl -sk -b "$1" "$BASE/notifications/unread-count" | grep -o '"count":[0-9]*' | cut -d: -f2; }
# максимальный id уведомлений пользователя (курсор для /wait)
last_id() {
  curl -sk -b "$1" "$BASE/notifications?size=50" | grep -o '"id":[0-9]*' | cut -d: -f2 | sort -n | tail -1
}
# цикл long polling до появления оповещения новее afterId: $1 jar, $2 afterId,
# $3 итераций по 30 сек (5 × 30 = 150 сек > интервал 1 мин + обход 1 мин).
# Печатает 1, если появилось, иначе 0; последний ответ — в $TMP/waitloop.json.
wait_new() {
  local jar=$1 after=$2 tries=${3:-5} i=0 code
  while [ "$i" -lt "$tries" ]; do
    code=$(curl -sk -b "$jar" -o "$TMP/waitloop.json" -w "%{http_code}" \
      "$BASE/notifications/wait?timeoutSec=30&afterId=${after:-0}")
    if [ "$code" != "200" ]; then
      echo 0
      return
    fi
    if ! grep -q '"content":\[\]' "$TMP/waitloop.json"; then
      echo 1
      return
    fi
    i=$((i + 1))
  done
  echo 0
}
# полный набор событий каталога in-app (для PUT preferences)
ALL_EVENTS='["NEW_TICKET","NEW_MESSAGE","NEW_SYSTEM_MESSAGE","CHANGE_TICKET","TICKET_DELETED","NEW_PORTAL","PORTAL_DELETED"]'

echo "═══ Оповещения ═══"

step "Регистрация владельца A и доверенного B"
s=$(code -c "$JAR_A" -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Alpha\",\"lastName\":\"Owner\",\"email\":\"$OWNER_EMAIL\",\"password\":\"Password123!\"}")
if [ "$s" = "429" ]; then
  echo "  FAIL лимит регистрации исчерпан — docker compose restart app и повторите скрипт"
  exit 1
fi
check "$s" "201" "A зарегистрирован"
check "$(code -c "$JAR_B" -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Bravo\",\"lastName\":\"Guest\",\"email\":\"$GUEST_EMAIL\",\"password\":\"Password123!\"}")" \
  "201" "B зарегистрирован"
$PSQL "update users set is_email_verified=true;" >/dev/null

step "A создаёт портал и выдаёт B доступ"
check "$(code -b "$JAR_A" -X POST "$BASE/portals" -H 'Content-Type: application/json' \
  -d '{"name":"notify-portal-'$RUN'","description":"оповещения"}')" "201" "портал создан"
PORTAL=$($PSQL "select p.id from portals p join users u on u.id=p.owner_id where u.email='$OWNER_EMAIL';")
GUEST_ID=$($PSQL "select id from users where email='$GUEST_EMAIL';")
check "$(code -b "$JAR_A" -X PUT "$BASE/portals/shared/$PORTAL/users" -H 'Content-Type: application/json' \
  -d "{\"userIds\":[\"$GUEST_ID\"]}")" "204" "B получил доступ"

step "Список и счётчик до событий"
check "$(code -b "$JAR_B" "$BASE/notifications")" "200" "GET /notifications доступен"
check "$(unread "$JAR_B")" "0" "у B непрочитанных нет"
check "$(code -b "$JAR_B" "$BASE/notifications/preferences")" "200" "GET /preferences доступен"

step "B ждёт long polling, пока A создаёт заявку"
curl -sk -b "$JAR_B" -o "$TMP/wait1.json" -w "%{http_code}" \
  "$BASE/notifications/wait?timeoutSec=15" > "$TMP/wait1.code" &
WAIT_PID=$!
sleep 1
check "$(code -b "$JAR_A" -X POST "$BASE/portals/$PORTAL/tickets" -H 'Content-Type: application/json' \
  -d '{"title":"Первая заявка","body":"тело заявки"}')" "201" "A создал заявку"
wait "$WAIT_PID"
check "$(cat "$TMP/wait1.code")" "200" "long polling B завершился 200"
if grep -q '"event":"NEW_TICKET"' "$TMP/wait1.json"; then
  echo "  OK   B получил NEW_TICKET по long polling"
else
  echo "  FAIL B не получил NEW_TICKET: $(cat "$TMP/wait1.json")"
  FAIL=1
fi
TICKET=$($PSQL "select id from ticket_model order by id desc limit 1;")

step "Автор действия не получает уведомления о своей заявке"
check "$(unread "$JAR_A")" "0" "у A непрочитанных нет (сам создал заявку)"
check "$(unread "$JAR_B")" "1" "у B одно непрочитанное"

step "B читает всё, владелец получает CHANGE_TICKET от действия B"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/read-all")" "204" "B отметил всё прочитанным"
check "$(unread "$JAR_B")" "0" "у B снова 0"
check "$(code -b "$JAR_B" -X PUT "$BASE/portals/$PORTAL/tickets/$TICKET/status" -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}')" "204" "B сменил статус заявки"
check "$(unread "$JAR_A")" "1" "у A уведомление о смене статуса (CHANGE_TICKET)"
check "$(unread "$JAR_B")" "0" "у B нет уведомления о собственном действии"
check "$(code -b "$JAR_A" -X PUT "$BASE/notifications/read-all")" "204" "A отметил всё прочитанным"

step "B отключает NEW_TICKET в настройках"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/preferences" -H 'Content-Type: application/json' \
  -d '{"events":["CHANGE_TICKET"]}')" "200" "настройки сохранены"
if curl -sk -b "$JAR_B" "$BASE/notifications/preferences" | grep -q '"CHANGE_TICKET"'; then
  echo "  OK   в настройках остался CHANGE_TICKET"
else
  echo "  FAIL настройки не сохранились"
  FAIL=1
fi

step "Отключённое событие не приходит: A создаёт вторую заявку"
AFTER=$(last_id "$JAR_B")
curl -sk -b "$JAR_B" -o "$TMP/wait2.json" -w "%{http_code}" \
  "$BASE/notifications/wait?timeoutSec=2&afterId=${AFTER:-0}" > "$TMP/wait2.code" &
WAIT_PID=$!
sleep 1
check "$(code -b "$JAR_A" -X POST "$BASE/portals/$PORTAL/tickets" -H 'Content-Type: application/json' \
  -d '{"title":"Вторая заявка","body":"снова тело"}')" "201" "A создал вторую заявку"
wait "$WAIT_PID"
check "$(cat "$TMP/wait2.code")" "200" "long polling B завершился 200 по таймауту"
if grep -q '"content":\[\]' "$TMP/wait2.json"; then
  echo "  OK   оповещение о NEW_TICKET не пришло (событие отключено)"
else
  echo "  FAIL ожидали пустой content: $(cat "$TMP/wait2.json")"
  FAIL=1
fi
check "$(unread "$JAR_B")" "0" "у B по-прежнему 0 непрочитанных"

step "B создаёт заявку — A (владелец) получает NEW_TICKET"
check "$(code -b "$JAR_B" -X POST "$BASE/portals/$PORTAL/tickets" -H 'Content-Type: application/json' \
  -d '{"title":"Заявка от B","body":"от доверенного"}')" "201" "B создал заявку"
check "$(unread "$JAR_A")" "1" "у A непрочитанное NEW_TICKET"

step "Повторы: настройки по умолчанию у пользователя без строки"
PREFS=$(curl -sk -b "$JAR_A" "$BASE/notifications/preferences")
if echo "$PREFS" | grep -q '"repeatEnabled":true' && echo "$PREFS" | grep -q '"repeatIntervalMinutes":30'; then
  echo "  OK   без строки настроек повтор включён на 30 минут"
else
  echo "  FAIL дефолт повтора не такой: $PREFS"
  FAIL=1
fi
check "$(code -b "$JAR_A" -X PUT "$BASE/notifications/read-all")" "204" "A дочитал хвост"

step "Повтор: напоминание о непрочитанной NEW_TICKET приходит каждую минуту"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/preferences" -H 'Content-Type: application/json' \
  -d "{\"events\":$ALL_EVENTS,\"repeatEnabled\":true,\"repeatIntervalMinutes\":1}")" "200" "повтор включён: каждую минуту"
PREFS=$(curl -sk -b "$JAR_B" "$BASE/notifications/preferences")
if echo "$PREFS" | grep -q '"repeatEnabled":true' && echo "$PREFS" | grep -q '"repeatIntervalMinutes":1'; then
  echo "  OK   настройки повтора сохранились (вкл, 1 мин)"
else
  echo "  FAIL настройки повтора не сохранились: $PREFS"
  FAIL=1
fi
check "$(unread "$JAR_B")" "0" "у B нет непрочитанных перед повтором"
check "$(code -b "$JAR_A" -X POST "$BASE/portals/$PORTAL/tickets" -H 'Content-Type: application/json' \
  -d '{"title":"Заявка для повтора","body":"напоминание"}')" "201" "A создал заявку для повтора"
check "$(unread "$JAR_B")" "1" "B получил исходное оповещение"
AFTER=$(last_id "$JAR_B")
check "$(wait_new "$JAR_B" "$AFTER" 5)" "1" "напоминание пришло по long polling"
check "$(unread "$JAR_B")" "2" "непрочитанных стало 2: исходное + напоминание"
REMINDER_ID=$(last_id "$JAR_B")
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/$REMINDER_ID/read")" "204" "напоминание отмечено прочитанным"
check "$(unread "$JAR_B")" "0" "прочтение одной строки погасило всю группу"

step "Повтор выключен — напоминания нет"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/preferences" -H 'Content-Type: application/json' \
  -d "{\"events\":$ALL_EVENTS,\"repeatEnabled\":false,\"repeatIntervalMinutes\":1}")" "200" "повтор выключен"
PREFS=$(curl -sk -b "$JAR_B" "$BASE/notifications/preferences")
if echo "$PREFS" | grep -q '"repeatEnabled":false'; then
  echo "  OK   выключение сохранилось"
else
  echo "  FAIL выключение не сохранилось: $PREFS"
  FAIL=1
fi
check "$(code -b "$JAR_A" -X POST "$BASE/portals/$PORTAL/tickets" -H 'Content-Type: application/json' \
  -d '{"title":"Заявка без повтора","body":"проверка"}')" "201" "A создал заявку"
check "$(unread "$JAR_B")" "1" "B получил исходное оповещение"
AFTER=$(last_id "$JAR_B")
check "$(wait_new "$JAR_B" "$AFTER" 5)" "0" "напоминание не пришло за 2.5 минуты (повтор выключен)"
check "$(unread "$JAR_B")" "1" "осталось только исходное оповещение"

step "Права и валидация"
check "$(code "$BASE/notifications")" "401" "без токена — 401"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/999999/read")" "404" "чужое/несуществующее уведомление — 404"
check "$(code -b "$JAR_B" -X PUT "$BASE/notifications/preferences" -H 'Content-Type: application/json' \
  -d '{"events":["SOME_UNKNOWN_EVENT"]}')" "400" "неизвестное событие в настройках — 400"
check "$(code -b "$JAR_B" "$BASE/notifications/wait?timeoutSec=99")" "400" "timeoutSec вне 1..30 — 400"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "═══ ИТОГ: все проверки прошли ═══"
else
  echo "═══ ИТОГ: есть проваленные проверки ═══"
fi
exit "$FAIL"
