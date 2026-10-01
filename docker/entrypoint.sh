#!/usr/bin/env bash
# Точка входа контейнера: поднимает keystore (если его не смонтировали) и
# запускает приложение.
#
# Keystore генерируется здесь, при старте контейнера, а не при сборке образа:
#   * пароль не оседает в слоях образа и в `docker history`
#   * смена пароля не требует пересборки — достаточно `docker compose up -d`
#   * сборка образа вообще не нуждается в секрете
#
# Если keystore смонтирован извне (prod: /app/ssl/keystore.p12), генерация
# пропускается и монтированный файл используется как есть.
set -eu

KEY_STORE_FILE=/app/ssl/keystore.p12

if [[ ! -f "$KEY_STORE_FILE" ]]; then
    : "${KEY_STORE_PASS:?KEY_STORE_PASS is required - copy .env.example to .env}"
    : "${KEY_STORE_ALIAS:?KEY_STORE_ALIAS is required - set it in services.app.environment}"

    keytool -genkeypair \
        -alias "$KEY_STORE_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -storetype PKCS12 \
        -keystore "$KEY_STORE_FILE" \
        -storepass "$KEY_STORE_PASS" \
        -dname "CN=localhost" \
        -validity 3650
fi

exec java \
    -XX:ReservedCodeCacheSize="${JAVA_RESERVED_CODE_CACHE_SIZE}" \
    -XX:MaxDirectMemorySize="${JAVA_MAX_DIRECT_MEMORY_SIZE}" \
    -XX:MaxMetaspaceSize="${JAVA_MAX_METASPACE_SIZE}" \
    "-Xss${JAVA_XSS}" \
    "-Xmx${JAVA_XMX}" \
    ${JAVA_HEAP_DUMP_OPTS} \
    ${JAVA_ON_OOM_OPTS} \
    ${JAVA_ERROR_FILE_OPTS} \
    ${JAVA_NMT_OPTS} \
    ${JAVA_GC_LOG_OPTS} \
    ${JAVA_JFR_OPTS} \
    ${JAVA_JMX_OPTS} \
    -jar app.jar
