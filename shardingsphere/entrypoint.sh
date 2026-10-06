#!/bin/bash
#
# Старт ShardingSphere-Proxy с конфигурацией, собранной из шаблонов.
#
# Прокси не умеет подставлять переменные окружения в свои yaml, а держать
# пароли в git нельзя (см. sec.md §4). Поэтому shardingsphere/conf — шаблоны
# с плейсхолдерами __NAME__, а здесь они заменяются на значения из окружения
# контейнера, и только после этого запускается start.sh.
#
# Подключение: psql "host=127.0.0.1 port=3307 dbname=<SHARD_DB> user=root"
#   (пароль — SHARDINGSPHERE_ROOT_PASSWORD). Шарды — внутренняя деталь прокси:
#   их порты (5433/5434) опубликованы только для отладки на хосте.
set -euo pipefail

TEMPLATE_DIR=${TEMPLATE_DIR:-/opt/shardingsphere/conf}
CONF_DIR=${CONF_DIR:-/opt/shardingsphere-proxy/conf}

# Без этих значений прокси стартовал бы с плейсхолдерами в конфиге или
# с паролем по умолчанию из git — поэтому обязательны, как и секреты app.
: "${PORT:?PORT is required}"
: "${SHARD_DB:?SHARD_DB is required}"
: "${SHARD_DB_USER:?SHARD_DB_USER is required}"
: "${SHARD_DB_PASSWORD:?SHARD_DB_PASSWORD is required}"
: "${SHARDINGSPHERE_ROOT_PASSWORD:?SHARDINGSPHERE_ROOT_PASSWORD is required}"
: "${SHARDINGSPHERE_SHARDING_PASSWORD:?SHARDINGSPHERE_SHARDING_PASSWORD is required}"

# Замена плейсхолдера на литерал: ${content//"<плейсхолдер>"/"<значение>"} —
# обе части в кавычках, чтобы спецсимволы пароля не воспринялись как шаблон.
render() {
    local template=$1
    local target=$2
    local content
    content=$(<"$template")
    content=${content//'__SHARD_DB__'/"$SHARD_DB"}
    content=${content//'__SHARD_DB_USER__'/"$SHARD_DB_USER"}
    content=${content//'__SHARD_DB_PASSWORD__'/"$SHARD_DB_PASSWORD"}
    content=${content//'__SHARDINGSPHERE_ROOT_PASSWORD__'/"$SHARDINGSPHERE_ROOT_PASSWORD"}
    content=${content//'__SHARDINGSPHERE_SHARDING_PASSWORD__'/"$SHARDINGSPHERE_SHARDING_PASSWORD"}
    printf '%s\n' "$content" >"$target"
}

render "$TEMPLATE_DIR/global.yaml" "$CONF_DIR/global.yaml"
render "$TEMPLATE_DIR/database-sharding.yaml" "$CONF_DIR/database-sharding.yaml"

# start.sh <port> — каталог конфигурации берётся по умолчанию ($CONF_DIR);
# в конфиге образа остаются logback.xml и неиспользуемые шаблоны правил.
exec /opt/shardingsphere-proxy/bin/start.sh "$PORT"
