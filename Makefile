# RealHelpDesk — быстрые команды разработки.
# Запуск: make help

MVN ?= mvn
COMPOSE ?= docker compose -f infrastructure/docker-compose.yaml
IMAGE ?= realhelpdesk:latest
APP_JAR ?= target/realhelpdesk-0.0.1-SNAPSHOT.jar
E2E_DIR ?= scripts

.DEFAULT_GOAL := help

.PHONY: help build clean compile package test verify format check run \
        dev-run dev-test \
        env docker-build up up-sharding down down-volumes logs ps restart shell check-image \
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
	$(COMPOSE) up --build

dev-test: ## Dev-среда: mvn verify + e2e-сценарии (нужен поднятый стек: dev-run/up)
	$(MVN) verify
	$(MAKE) e2e-all

## --- Docker ---

env: ## Создать infrastructure/.env из infrastructure/.env.example, если его нет
	@test -f infrastructure/.env || (cp infrastructure/.env.example infrastructure/.env && echo "Создан infrastructure/.env — заполните секреты")

docker-build: ## Собрать docker-образ приложения
	$(COMPOSE) build

up: env ## Поднять весь стек (сборка образа, в фоне)
	$(COMPOSE) up --build -d

up-sharding: env ## Поднять стек с профилем sharding
	$(COMPOSE) --profile sharding up --build -d

down: ## Остановить стек
	$(COMPOSE) down

down-volumes: ## Остановить стек и удалить тома (данные БД/MinIO)
	$(COMPOSE) down -v

logs: ## Логи приложения (follow)
	$(COMPOSE) logs -f app

ps: ## Статус контейнеров
	$(COMPOSE) ps

restart: ## Перезапустить только приложение
	$(COMPOSE) restart app

shell: ## Shell внутри контейнера app
	$(COMPOSE) exec app sh

check-image: docker-build ## Проверки образа: секреты, ФС, jar
	./scripts/docker/check-image.sh $(IMAGE)

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
