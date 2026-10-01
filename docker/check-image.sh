#!/usr/bin/env bash
# Проверки собранного образа. Ненулевой код возврата = найдено нарушение.
# Вызов: docker/check-image.sh [образ]   (по умолчанию realhelpdesk:latest)
set -u
IMG="${1:-realhelpdesk:latest}"
fail=0

report() {                     # $1 = находки (пусто = ок), $2 = описание
  if [ -n "$1" ]; then
    printf 'FAIL %s\n%s\n' "$2" "$1"
    fail=1
  else
    printf 'OK   %s\n' "$2"
  fi
}

# 1. Имена секретов не встречаются ни в одной инструкции сборки.
#    (Значения build-arg'ов BuildKit раскрывает в истории: там видно
#     и "ARG SECRET=...", и "RUN |1 SECRET=... keytool ...".)
out=$(docker history --no-trunc "$IMG" \
      | grep -iE 'JWT_SECRET|DB_PASSWORD|KEY_STORE_PASS|MAIL_PASSWORD|storepass' || true)
report "$out" "docker history без имён секретов"

# 2. Переменные окружения образа — только конфиг, без секретов.
out=$(docker inspect "$IMG" --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | grep -iE 'SECRET|PASSWORD' || true)
report "$out" "Config.Env без секретов"

# 3. Файловая система контейнера: нет ни .env, ни keystore.
#    --entrypoint sh отключает entrypoint, то есть keytool не успел
#    сработать и мы смотрим образ как есть. 2>/dev/null обязателен:
#    контейнер работает не от root, и find иначе печатает Permission denied.
out=$(docker run --rm --entrypoint sh "$IMG" \
      -c 'find / -xdev \( -name .env -o -name "*.p12" \) -not -path "/proc/*" 2>/dev/null' || true)
report "$out" "ФС контейнера без .env и *.p12"

# 4. Ключевой материал не запечён внутрь jar. find из п. 3 архив не открывает,
#    поэтому jar проверяется отдельно — на хосте (unzip/jar в runtime-образе нет).
for jar in target/*.jar; do
  [ -e "$jar" ] || continue
  out=$(unzip -l "$jar" 2>/dev/null | grep -iE '\.(p12|pem|key|jks)$' || true)
  report "$out" "$jar без ключевого материала"
done

# 5. Если установлен trivy — скан на секреты.
if command -v trivy >/dev/null 2>&1; then
  trivy image --scanners secret "$IMG" || fail=1
else
  printf 'SKIP trivy (не установлен: https://trivy.dev/latest/getting-started/installation/)\n'
fi

exit "$fail"
