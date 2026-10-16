# RealHelpDesk — быстрые команды разработки.
# Запуск: make help

MVN ?= mvn
IMAGE ?= realhelpdesk:latest
APP_JAR ?= target/realhelpdesk-0.0.1-SNAPSHOT.jar
E2E_DIR ?= scripts

# Ревизия git для LABEL образа (org.opencontainers.image.revision)
GIT_SHA ?= $(shell git rev-parse HEAD 2>/dev/null || echo unknown)

# Наборы compose-файлов: база всегда, дальше — только своя.
# Явные -f отключают автоподхват docker-compose.override.yaml — рабочий стек
# из override никогда не собирается.
COMPOSE_DEV ?= docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml
COMPOSE_PROD ?= docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.prod.yaml
COMPOSE_LOAD ?= docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml -f infrastructure/docker-compose.load.yaml
COMPOSE_OBSERVABILITY ?= docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml -f infrastructure/docker-compose.observability.yaml

.DEFAULT_GOAL := help

.PHONY: help build clean compile package test verify format check run \
        dev-run dev-test \
        env docker-build up up-prod up-load down down-volumes logs ps restart shell check-image \
        obs-up obs-down obs-open obs-reload obs-alerts \
        load load-smoke \
        migrate \
        openapi openapi-types \
        e2e-portal-access e2e-ratelimit e2e-jwt e2e-notifications e2e-portal-transfer e2e-all

help: ## Показать список команд
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

## --- Сборка и тесты (Maven, на хосте) ---

build: ## Собрать jar без тестов (package -DskipTests)
	$(MVN) -DskipTests package

clean: ## Очистить target/
	$(MVN) clean

compile: ## Компиляция main и test
	$(MVN) compile test-compile

package: ## Полная сборка: тесты + jar
	$(MVN) package

test: ## Юнит-тесты (surefire)
	$(MVN) test

verify: ## Тесты + интеграционные *IT (failsafe)
	$(MVN) verify

format: ## Форматирование кода (spotless:apply)
	$(MVN) spotless:apply

check: ## Проверки стиля (spotless:check + checkstyle)
	$(MVN) spotless:check checkstyle:check

run: ## Запустить приложение локально (spring-boot:run)
	$(MVN) spring-boot:run

dev-run: env ## Dev-среда: compose up в foreground (логи всех сервисов, Ctrl+C — стоп)
	$(COMPOSE_DEV) up --build

dev-test: ## Dev-среда: mvn verify + e2e-сценарии (нужен поднятый стек: dev-run/up)
	$(MVN) verify
	$(MAKE) e2e-all

## --- Docker ---

env: ## Создать infrastructure/.env из infrastructure/.env.example, если его нет
	@test -f infrastructure/.env || (cp infrastructure/.env.example infrastructure/.env && echo "Создан infrastructure/.env — заполните секреты")

docker-build: ## Собрать docker-образ приложения (с меткой GIT_SHA)
	$(COMPOSE_DEV) build --build-arg GIT_SHA=$(GIT_SHA)

up: env ## Поднять локальный стек (сборка образа, в фоне)
	$(COMPOSE_DEV) up --build -d

up-prod: env ## Поднять рабочий стек (без override; заполните stack.prod.env)
	$(COMPOSE_PROD) up --build -d

down: ## Остановить локальный стек
	$(COMPOSE_DEV) down

down-volumes: ## Остановить стек и удалить тома (данные БД/MinIO)
	$(COMPOSE_DEV) down -v

## --- Наблюдаемость (подробности: docs/observability/overview.md) ---

obs-up: env ## Поднять стек + наблюдаемость (Grafana: http://localhost:3001, Mimir: :9090)
	$(COMPOSE_OBSERVABILITY) up --build -d --remove-orphans

obs-down: ## Остановить стек с наблюдаемостью (томы целы)
	$(COMPOSE_OBSERVABILITY) down

obs-open: ## Открыть Grafana в браузере (нужен obs-up; порт GRAFANA_PORT, дефолт 3001)
	@xdg-open http://localhost:3001 >/dev/null 2>&1 || open http://localhost:3001 >/dev/null 2>&1 || echo "Grafana: http://localhost:3001"

obs-reload: ## Перечитать правила алертов Mimir (нужен obs-up; mimir/rules/anonymous/realhelpdesk.yml)
	$(COMPOSE_OBSERVABILITY) restart mimir && echo "Mimir перезапущен — правила перечитаны"

obs-alerts: ## Текущие состояния алертов Mimir (нужен obs-up)
	curl -s localhost:9090/prometheus/api/v1/alerts | grep -oE '"alertname":"[^"]+"|"state":"[^"]+"' | paste - - || echo "Mimir недоступен"

logs: ## Логи приложения (follow)
	$(COMPOSE_DEV) logs -f app

ps: ## Статус контейнеров
	$(COMPOSE_DEV) ps

restart: ## Перезапустить только приложение
	$(COMPOSE_DEV) restart app

shell: ## Shell внутри контейнера app
	$(COMPOSE_DEV) exec app sh

check-image: docker-build ## Проверки образа: секреты, ФС, jar
	./scripts/docker/check-image.sh $(IMAGE)

## --- Нагрузочное тестирование (k6, scripts/load/) ---

up-load: env ## Поднять стек с load-оверлеем (TRUST_PROXY_HEADERS=true, только локально)
	$(COMPOSE_LOAD) up --build -d

load: up-load ## Нагрузочный user journey: TARGET_VUS=10 DURATION=5m POOL_SIZE=8 (env)
	bash scripts/load/run.sh

load-smoke: up-load ## Короткий smoke-прогон user journey (2 VU, 1 минута)
	TARGET_VUS=2 DURATION=1m bash scripts/load/run.sh

## --- Миграции схемы (Liquibase с хоста; порты БД проброшены на 127.0.0.1) ---
# Миграции также применяет само приложение при старте (DB_MIGRATIONS_ENABLED).
# Цели ниже — для чистого/ручного прогона без контейнера приложения.

migrate: env ## Миграции Liquibase на основную БД (localhost:5432)
	@set -a && . infrastructure/.env && set +a && \
	$(MVN) -q liquibase:update \
	  -Dliquibase.url=jdbc:postgresql://127.0.0.1:5432/desk \
	  -Dliquibase.username=user \
	  -Dliquibase.password="$$DB_PASSWORD"

## --- OpenAPI (нужен поднятый стек: make up) ---

openapi: ## Забрать OpenAPI-спеку с запущенного стека в openapi.json
	curl -sk https://localhost:8443/v3/api-docs -o openapi.json && echo "openapi.json обновлён"

openapi-types: openapi ## Сгенерировать TypeScript-типы из спеки (нужен npx)
	npx --yes openapi-typescript openapi.json -o web/src/api/schema.d.ts

## --- E2E (нужен поднятый стек: make up) ---

e2e-portal-access: ## E2E: права на портал
	bash $(E2E_DIR)/e2e-portal-access.sh

e2e-ratelimit: ## E2E: рейт-лимиты
	bash $(E2E_DIR)/e2e-ratelimit.sh

e2e-jwt: ## E2E: JWT-поток аутентификации
	bash $(E2E_DIR)/e2e-jwt-flow.sh

e2e-notifications: ## E2E: in-app оповещения (long polling)
	bash $(E2E_DIR)/e2e-notifications.sh

e2e-portal-transfer: ## E2E: передача владения порталом
	bash $(E2E_DIR)/e2e-portal-transfer.sh

e2e-all: ## E2E: все сценарии подряд (тратит рейт-лимиты!)
	bash $(E2E_DIR)/e2e-portal-access.sh
	bash $(E2E_DIR)/e2e-ratelimit.sh
	bash $(E2E_DIR)/e2e-jwt-flow.sh
	bash $(E2E_DIR)/e2e-notifications.sh
	bash $(E2E_DIR)/e2e-portal-transfer.sh
