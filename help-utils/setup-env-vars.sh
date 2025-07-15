#!/bin/bash

# APP
export APP_NAME="Real help desk"
export APP_DOMAIN_NAME="example.com"

# SERVER
export SERVER_PORT="8443"
export KEY_STORE="keystore.p12"
export KEY_STORE_PASS="$(openssl rand -base64 24 | tr -d '=+/[:space:]' | cut -c1-20)"
export KEY_STORE_ALIAS="tomcat"

# Logs
export LOG_LEVEL_SECURITY="INFO"
export LOG_LEVEL_HIBERNATE_SQL="WARN"
export LOG_LEVEL_HIBERNATE_BINDER="INFO"

# PostgreSQL
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="desk"
export DB_USER="user"
export DB_PASS="pass"

# JPA / Hibernate
export JPA_DDL_AUTO="create-drop"
export JPA_SHOW_SQL="false"
export JPA_OSIV="false"

# JWT
SECRET=$(openssl rand -base64 32)
echo "JWT_SECRET: $SECRET"
export JWT_SECRET="$SECRET"
export JWT_ISSUER="https://$APP_DOMAIN_NAME"
export JWT_AUDIENCE="https://$APP_DOMAIN_NAME"

# MAIL - SMTP
export MAIL_HOST="localhost"
export MAIL_PORT="25"
export MAIL_USER="user@example.com"
export MAIL_PASSWORD="password"
export SMTP_AUTH="false"
export SMTP_STARTTLS="false"

# MAIL - Emails
export EMAIL_DOMAIN="example.com"
export MAIL_FROM="noreply@${EMAIL_DOMAIN}"
export MAIL_NOTIFY="admin@${EMAIL_DOMAIN}"

echo ""
echo "========================"
echo "Сводка конфигурации:"
echo "========================"
echo "Название приложения  : $APP_NAME"
echo "Домен                : $APP_DOMAIN_NAME"
echo "Порт сервера         : $SERVER_PORT"
echo "Keystore файл        : $KEY_STORE"
echo "Keystore пароль      : $KEY_STORE_PASS"
echo "Keystore alias       : $KEY_STORE_ALIAS"
echo "База данных          : $DB_NAME@$DB_HOST:$DB_PORT"
echo "Пользователь БД      : $DB_USER"
echo "JWT Issuer           : $JWT_ISSUER"
echo "JWT Audience         : $JWT_AUDIENCE"
echo "JWT Secret           : $JWT_SECRET"
echo "Почта отправителя    : $MAIL_FROM"
echo "Уведомления на       : $MAIL_NOTIFY"
echo "========================"
echo "Done!"
