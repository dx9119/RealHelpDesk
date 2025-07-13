#!/bin/bash

# Указываем каталог для SSL-файлов
TARGET_DIR="../target"
SSL_DIR="$TARGET_DIR/ssl"

# Проверяем наличие каталога target
if [ ! -d "$TARGET_DIR" ]; then
  echo "Ошибка: каталог $TARGET_DIR не существует. Сначала нужно собрать проект."
  return 1
fi

# Создаём ssl-подкаталог, если он отсутствует
if [ ! -d "$SSL_DIR" ]; then
  mkdir -p "$SSL_DIR" || { echo "Ошибка: не удалось создать каталог $SSL_DIR"; return 1; }
fi

# Получаем CN (Common Name) из переменной окружения
CERT_CN="${APP_DOMAIN_NAME:-example.com}"

if [ -z "$CERT_CN" ]; then
  echo "Ошибка: APP_DOMAIN_NAME не задан, а значение по умолчанию отсутствует."
  return 1
fi

echo "Используется CN (Common Name): $CERT_CN"

# Проверяем наличие всех необходимых переменных окружения
REQUIRED_VARS=("KEY_STORE" "KEY_STORE_PASS" "KEY_STORE_ALIAS")
for var in "${REQUIRED_VARS[@]}"; do
  eval "value=\$$var"
  if [ -z "$value" ]; then
    echo "Ошибка: переменная $var не задана."
    return 1
  fi
done

# Формируем полный путь к Keystore файлу
FULL_KEY_STORE_PATH="$SSL_DIR/$KEY_STORE"

# Проверяем, существует ли Keystore файл, и предлагаем выбор действия
if [ -f "$FULL_KEY_STORE_PATH" ]; then
  echo "Файл Keystore уже существует: $FULL_KEY_STORE_PATH."
  read -p "Что вы хотите сделать? (1 - перезаписать, 2 - остановить выполнение): " choice
  case "$choice" in
    1)
      echo "Выбрана перезапись файла."
      ;;
    2)
      echo "Генерация отменена."
      return 0
      ;;
    *)
      echo "Неверный выбор. Генерация отменена."
      return 1
      ;;
  esac
fi

echo "Генерация Keystore для CN: $CERT_CN в $FULL_KEY_STORE_PATH..."

TMP_KEY="key.tmp.pem"
TMP_CERT="cert.tmp.pem"

# Генерируем RSA ключ
openssl genrsa -out "$TMP_KEY" 2048 || { echo "Ошибка при генерации RSA ключа."; rm -f "$TMP_KEY" "$TMP_CERT"; return 1; }

# Создаём сертификат
openssl req -new -x509 -key "$TMP_KEY" -out "$TMP_CERT" -days "365" -subj "/CN=$CERT_CN" || {
  echo "Ошибка при создании сертификата."
  rm -f "$TMP_KEY" "$TMP_CERT"
  return 1
}

# Экспортируем ключ и сертификат
openssl pkcs12 -export -in "$TMP_CERT" -inkey "$TMP_KEY" -out "$FULL_KEY_STORE_PATH" \
  -name "$KEY_STORE_ALIAS" -password pass:"$KEY_STORE_PASS" || {
  echo "Ошибка при экспорте в PKCS12."
  rm -f "$TMP_KEY" "$TMP_CERT"
  return 1
}

echo "Keystore успешно создан: $FULL_KEY_STORE_PATH"

# Явная очистка временных файлов
rm -f "$TMP_KEY" "$TMP_CERT"
echo "========================"
echo "========================"
echo "========================"
return 0
