#!/bin/bash

# Проверка аргумента: CN
CERT_CN="$1"
if [ -z "$CERT_CN" ]; then
  echo "Ошибка: необходимо указать CN при запуске (например: mydomain.com)"
  echo "Пример: ./generate-keystore.sh mydomain.com"
  return 1
fi

# Проверка переменных окружения
REQUIRED_VARS=("KEY_STORE" "KEY_STORE_PASS" "KEY_STORE_ALIAS")
for var in "${REQUIRED_VARS[@]}"; do
  eval "value=\$$var"
  if [ -z "$value" ]; then
    echo "Ошибка: переменная $var не задана"
    return 1
  fi
done

# Пропуск, если файл уже существует
if [ -f "$KEY_STORE" ]; then
  echo "Файл уже существует: $KEY_STORE"
  return 0
fi

# Генерация ключа и сертификата
openssl genrsa -out key.tmp.pem 2048
openssl req -new -x509 -key key.tmp.pem -out cert.tmp.pem -days "360" -subj "/CN=$CERT_CN"
openssl pkcs12 -export -in cert.tmp.pem -inkey key.tmp.pem -out "$KEY_STORE" \
  -name "$KEY_STORE_ALIAS" -password pass:"$KEY_STORE_PASS"

# Очистка временных файлов
rm key.tmp.pem cert.tmp.pem

echo "Keystore создан: $KEY_STORE"
