#!/bin/bash

# APP
export APP_NAME="my-spring-application"

# SERVER
export SERVER_PORT="8080"
export KEY_STORE="keystore.p12"
export KEY_STORE_PASS="changeit"
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
export JPA_DDL_AUTO="none"
export JPA_SHOW_SQL="false"
export JPA_OSIV="false"

# JWT
SECRET=$(openssl rand -base64 32)
export JWT_SECRET="$SECRET"
export JWT_ISSUER="your-app-issuer"
export JWT_AUDIENCE="your-app-audience"

# MAIL - SMTP
export MAIL_HOST="localhost"
export MAIL_PORT="25"
export MAIL_USER="user@example.com"
export MAIL_PASSWORD="password"
export SMTP_AUTH="false"
export SMTP_STARTTLS="false"

# MAIL - Emails
export MAIL_FROM="noreply@example.com"
export MAIL_NOTIFY="admin@example.com"

echo "Done!"
