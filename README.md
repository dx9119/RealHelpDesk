# RealHelpDesk API

REST API для системы управления заявками. Пользователи создают **порталы** - тематические
пространства, внутри которых формируются **заявки**, заявки содержат - **сообщения** и **вложения**.

## Возможности

- Регистрация, авторизация, аутентификация, верификация email, восстановление пароля.
- Порталы: создание, редактирование, удаление; публичный доступ и доступ по
  списку доаверенных пользователей.
- Заявки: создание, статусы, приоритеты, удаление, поиск по заявкам.
- Формирование сообщений, прикрепление к заявке файловых вложений (MinIO, отдача с поддержкой HTTP `Range` для не стабильных сетей).
- Передача права владения порталом с подтверждением и полной историей состояний портала.
- Оповещения: email и in-app (HTTP long polling), настройки триггеров и повторов оповещений.
- Ограничения: квоты пользователя, рейт-лимиты, капча.

## Технологии

| Слой | Технологии                                                             |
|---|------------------------------------------------------------------------|
| Язык / сборка | Java 21, Maven                                                         |
| Фреймворк | Spring Boot 4, Spring Security, Spring Data JPA, Spring Validation     |
| БД / миграции | PostgreSQL 18, Liquibase (`ddl-auto=validate`)                         |
| Аутентификация | JWT (jjwt) в http-only cookies, refresh-токен хранится как SHA-256 хеш |
| Файлы | MinIO (метаданные в БД, содержимое — в бакете)                         |
| Почта | Spring Mail, шаблоны сообщений в `messages.properties`                 |
| Прочее | Lombok, Hibernate Validator, Spotless, Checkstyle                     |

## Архитектура

```
com.ukhanov.realhelpdesk
├── core      — конфигурация (Security/CORS), фильтры, JWT, пользователи,
│               почта, пагинация, рейт-лимиты, капча, контроль доступа, storage
├── domain    — JPA-сущности, репозитории и доменные сервисы
└── feature   — контроллеры и сервисы по областям: порталы, заявки, сообщения,
                вложения, пользователи, оповещения
```

Правила доступа к порталам и заявкам проверяются через `@PreAuthorize`
(`AccessValidationService`, `TicketAccessValidationService`). Ошибки
централизованно преобразуются в RFC 7807-подобные ответы `GlobalExceptionHandler`.

## Быстрый старт

Проект рассчитан на запуск в Docker (переменные окружения обязательны).

| Шаг | Команда | Что делает |
|---|---|---|
| 1 | `cp infrastructure/.env.example infrastructure/.env` | Шаблон секретов: заполните `JWT_SECRET`, `DB_PASSWORD`, `KEY_STORE_PASS`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` |
| 2 | `make up` | Сборка образа и запуск всего стека в фоне (`docker compose up --build -d`) |
| 3 | `make dev-run` | Dev-среда в foreground: логи всех сервисов, `Ctrl+C` — стоп |

API — `https://localhost:8443`. Вспомогательные сервисы: smtp4dev
(`http://localhost:3000`), PostgreSQL (`127.0.0.1:5432`), MinIO (`127.0.0.1:9000`).
Полный список команд — `make help`.

## Конфигурация

Схема приложения (`src/main/resources/application.properties`,
`application-domains.properties`) содержит только обязательные `${VAR}` без
дефолтов: отсутствующая переменная валит старт с
`Could not resolve placeholder '<VAR>'` — тихих подстановок нет. Значения
живут в трёх местах:

| Файл | Что там |
|---|---|
| `scripts/docker/stack.env` | Все несекретные значения: рейт-лимиты, клиентский IP, капча, сроки JWT, почта, MinIO, порты, домены. Сервисы compose получают его через `env_file`; правится без пересборки образа |
| `infrastructure/.env` | Секреты: `JWT_SECRET`, `DB_PASSWORD`, `KEY_STORE_PASS`, `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`, `SHARDINGSPHERE_*`. Шаблон — `infrastructure/.env.example`, в git не попадает |
| `services.app.environment` в `infrastructure/docker-compose.yaml` | Передача секретов из `.env` в контейнер (`${VAR:?}` — пустой или отсутствующий секрет валит запуск) |

Каждая переменная схемы объявлена ровно в одном из этих мест — парность
сверяет `ApplicationConfigSchemaTest` (`make test`).

### Клиентский IP

Логи (MDC `clientIp`) и ключ рейт-лимита получают адрес посетителя из одного
резолвера (`ClientIpResolver`), настройки — `client-ip.*` в
`scripts/docker/stack.env`:

| Переменная | Назначение |
|---|---|
| `TRUST_PROXY_HEADERS` | `false` (по умолчанию) — всегда адрес сокета запроса; `true` — из заголовков прокси. Включать только за прокси: иначе клиент присылает свой заголовок и подставляет чужой IP в логи и в счётчик лимита |
| `CLIENT_IP_HEADERS` | Заголовки через запятую в порядке приоритета, берётся первый пригодный: `X-Forwarded-For`, `X-Real-IP` (nginx), `CF-Connecting-IP` (Cloudflare), `True-Client-IP`, `X-Client-IP`, `Proxy-Client-IP`/`WL-Proxy-Client-IP` (WebLogic), `Forwarded` (RFC 7239, `for=1.2.3.4;proto=https`) |
| `CLIENT_IP_FORWARDED_INDEX` | Какой элемент списка в значении брать: `0` — первый слева (адрес клиента), отрицательный — справа (`-1` — ближайший прокси). За цепочкой из нескольких прокси ставьте `-1`: слева в `X-Forwarded-For` пишет клиент |

### Профили

Профиль задаётся `SPRING_PROFILES_ACTIVE` в `scripts/docker/stack.env` и
меняет только уровни логирования:

| Профиль | Файл | Что включает |
|---|---|---|
| `debug` (по умолчанию) | `application-debug.properties` | Локальная отладка: DEBUG по приложению, SQL-логи Hibernate без значений параметров |
| `prod` | `application-prod.properties` | Рабочий контур: INFO/WARN, пошаговая трассировка и SQL выключены |

### Профиль compose `sharding`

Шардирование PostgreSQL через ShardingSphere-Proxy (`127.0.0.1:3307`) по
умолчанию не поднимается. Включение: `make up-sharding` либо
`COMPOSE_PROFILES=sharding` в `infrastructure/.env`. Пароли прокси —
`SHARDINGSPHERE_*` в `infrastructure/.env`, шаблоны —
`infrastructure/shardingsphere/conf/`.

### Миграции

Структуру БД создаёт и обновляет Liquibase при старте; Hibernate только сверяет
сущности со схемой (`spring.jpa.hibernate.ddl-auto=validate`). Миграции —
`src/main/resources/db/changelog` (baseline `001-baseline-schema` + новые
changeset'ы). Рестарт `app` данные не трогает, `make down-volumes` удаляет том
PostgreSQL.

## Аутентификация

JWT передаётся в http-only cookie (`accessToken`), refresh-токен — в cookie с
`path=/api/v1/auth`. Каждый запрос проходит через `JwtAuthFilter`: проверяются
подпись, срок, issuer/audience, тип токена (`typ=access`), статус пользователя и
версия токенов (`ver`). Логаут и смена пароля немедленно отзывают access-токены.

Поток аутентификации:

| Шаг | Запрос | Что происходит |
|---|---|---|
| 1 | `POST /api/v1/auth/register` или `POST /api/v1/auth/login` | Сервер ставит cookies, тела нет |
| 2 | `POST /api/v1/auth/tokens/access` (по refresh-cookie) | Новый access и ротация refresh; старые refresh отзываются |
| 3 | `DELETE /api/v1/auth/session` | Логаут, отзыв access и refresh |

Настройки cookie: `jwt.cookie.same-site` (`JWT_COOKIE_SAMESITE`) и
`jwt.cookie.domain` (`JWT_COOKIE_DOMAIN`). Детали аудита — [`docs/jwt-audit.md`](docs/jwt-audit.md).

## API

Описание всех эндпоинтов — [`docs/api.md`](docs/api.md).

## Рейт-лимиты

Счётчик ведётся по IP и эндпоинту; при превышении — `429` с `Retry-After`.
Пороги заданы в `scripts/docker/stack.env` (`RATE_LIMIT_<KEY>_REQUESTS` /
`RATE_LIMIT_<KEY>_WINDOW_SECONDS`).

| Ключ | Эндпоинт | Лимит |
|---|---|---|
| `captcha` | `GET /captcha` | 30 / 60 c |
| `auth-register` | `POST /auth/register` | 10 / 5 мин |
| `auth-login` | `POST /auth/login` | 10 / 5 мин |
| `auth-access-token` | `POST /auth/tokens/access` | 30 / 5 мин |
| `password-reset-request` | `POST /users/password-resets` | 3 / 10 мин |
| `password-reset-confirm` | `PUT /users/password-resets/{code}` | 5 / 10 мин |
| `email-code` | `POST /email/codes` | 3 / 10 мин |
| `notifications-wait` | `GET /notifications/wait` | 30 / 60 c |
| `portal-transfer-request` | `POST /portals/{id}/owner-transfer` | 5 / 5 мин |
| `portal-transfer-confirm` | `POST /portals/{id}/owner-transfer/confirm` | 5 / 5 мин |
| `email-recovery` | письма восстановления (на адрес) | 3 / 24 ч |

Новый лимит — аннотация `@RateLimit(key = "...")` на методе контроллера плюс
два порога в `scripts/docker/stack.env`. Счётчики хранятся в памяти и сбрасываются при
рестарте. За прокси задайте `TRUST_PROXY_HEADERS=true` в `scripts/docker/stack.env`
(см. «Клиентский IP»), иначе все запросы будут считаться с одного IP.

## Тесты и проверки

**Все тесты проводятся только в dev среде.** Юнит- и интеграционные — на хосте,
без Docker, в тестовой конфигурации `src/test/resources/application.properties`;
e2e — только на поднятом dev-стеке (`make dev-run` или `make up`). В других
средах тесты не запускаются.

| Команда | Что делает |
|---|---|
| `make test` | Юнит-тесты и срезы (`@DataJpaTest` на H2) |
| `make verify` | То же + интеграционные `*IT` (failsafe, GreenMail) |
| `make format` | Форматирование (`spotless:apply`) |
| `make check` | Проверки стиля (`spotless:check`, `checkstyle`) |
| `make dev-run` | Dev-среда: сборка и запуск стека в foreground (логи всех сервисов) |
| `make dev-test` | Dev-среда: `mvn verify` + все e2e-сценарии (нужен поднятый стек) |

E2E-сценарии идут по живому стеку (`make up`) — все сразу через `make e2e-all`
или по одному:

| Команда | Сценарий | Скрипт |
|---|---|---|
| `make e2e-portal-access` | Права доступа к порталу | `scripts/e2e-portal-access.sh` |
| `make e2e-ratelimit` | Рейт-лимиты | `scripts/e2e-ratelimit.sh` |
| `make e2e-jwt` | JWT-поток аутентификации | `scripts/e2e-jwt-flow.sh` |
| `make e2e-notifications` | In-app оповещения (long polling) | `scripts/e2e-notifications.sh` |
| `make e2e-portal-transfer` | Передача владения порталом | `scripts/e2e-portal-transfer.sh` |
| `make e2e-all` | Все сценарии подряд | `scripts/e2e-*.sh` |

Скрипты создают тестовых пользователей и тратят рейт-лимиты — при повторных
прогонах может потребоваться `make restart`.

