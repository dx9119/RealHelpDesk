# RealHelpDesk API

REST API для системы управления заявками (help desk) или внутренней системы
обработки запросов. Пользователи создают **порталы** — тематические
пространства, внутри которых заводятся **заявки**, а в заявках — **сообщения**
и **вложения**.

## Возможности

- Регистрация, вход, верификация email, восстановление пароля.
- Порталы: создание, редактирование, удаление; публичный доступ и доступ по
  списку пользователей.
- Заявки: создание, статусы, приоритеты, удаление, поиск.
- Сообщения и файловые вложения (MinIO, отдача с поддержкой HTTP `Range`).
- Передача владения порталом с подтверждением и полной историей изменений.
- Оповещения: email и in-app (HTTP long polling), настройки событий и повторов.
- Ограничения: квоты пользователя, рейт-лимиты, капча.

## Технологии

| Слой | Технологии |
|---|---|
| Язык / сборка | Java 21, Maven |
| Фреймворк | Spring Boot 4, Spring Security, Spring Data JPA, Spring Validation |
| БД / миграции | PostgreSQL 18, Liquibase (`ddl-auto=validate`) |
| Аутентификация | JWT (jjwt) в http-only cookies, refresh-токен хранится как SHA-256 хеш |
| Файлы | MinIO (метаданные в БД, содержимое — в бакете) |
| Почта | Spring Mail, шаблоны в `messages.properties` |
| Прочее | Hibernate Validator, Spotless, Checkstyle |

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

```bash
cp .env.example .env        # заполните JWT_SECRET, DB_PASSWORD, KEY_STORE_PASS
make up                     # = docker compose up --build -d
```

API — `https://localhost:8443`. Вспомогательные сервисы: smtp4dev
(`http://localhost:3000`), PostgreSQL (`127.0.0.1:5432`), MinIO (`127.0.0.1:9000`).
Полный список команд — `make help`, детали сборки и секретов — [`sec.md`](sec.md).

Структуру БД создаёт и обновляет Liquibase при старте; Hibernate только сверяет
сущности со схемой (`spring.jpa.hibernate.ddl-auto=validate`). Миграции —
`src/main/resources/db/changelog` (baseline `001-baseline-schema` + новые
changeset'ы). Рестарт `app` данные не трогает, `make down-volumes` удаляет том
PostgreSQL.

## Конфигурация

| Файл | Что в нём |
|---|---|
| `src/main/resources/application.properties` | Схема обязательных `${VAR}` без дефолтов; `ddl-auto=validate` закреплён в коде |
| `src/main/resources/application-domains.properties` | Домены: CORS, issuer/audience токенов, домен фронта в письмах, адрес отправителя |
| `docker/app.env` | Продукт: рейт-лимиты, капча, сроки токенов, брендинг, адреса писем, MinIO |
| `docker-compose.yaml` → `services.app.environment` | Окружение: порты, хосты, ресурсы, JVM, профиль и секреты через `${VAR:?}` |
| `.env` | Только секреты: `JWT_SECRET`, `DB_PASSWORD`, `MAIL_PASSWORD`, `KEY_STORE_PASS`, `SHARDINGSPHERE_*` |

Профили логирования: `debug` (подробно, с SQL) и `prod` (INFO+, без секретов и
query-string). Задаётся `SPRING_PROFILES_ACTIVE`.

## Аутентификация

JWT передаётся в http-only cookie (`accessToken`), refresh-токен — в cookie с
`path=/api/v1/auth`. Каждый запрос проходит через `JwtAuthFilter`: проверяются
подпись, срок, issuer/audience, тип токена (`typ=access`), статус пользователя и
версия токенов (`ver`). Логаут и смена пароля немедленно отзывают access-токены.

Поток:

1. `POST /api/v1/auth/register` или `/login` → сервер ставит cookies, тела нет.
2. `POST /api/v1/auth/tokens/access` (по refresh-cookie) → новый access и
   ротация refresh; старые refresh отзываются.
3. `DELETE /api/v1/auth/session` → логаут, отзыв access и refresh.

Настройки cookie: `jwt.cookie.same-site` (`JWT_COOKIE_SAMESITE`) и
`jwt.cookie.domain` (`JWT_COOKIE_DOMAIN`). Детали аудита — [`docs/jwt-audit.md`](docs/jwt-audit.md).

## API

Базовый URL — `/api/v1`. Пагинированные ответы имеют вид `PageResponse`:
`{ content, page, size, totalElements, totalPages, last }`.

### Служебные

| Метод и путь | Назначение |
|---|---|
| `GET /health` | Проверка живости |
| `GET /captcha?capId=` | PNG-капча; `capId` — идентификатор посетителя (≤ 10 символов) |

### Аутентификация (`/auth`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /register` | `RegisterRequest` + опц. `?capId=` | Регистрация; ставит cookies |
| `POST /login` | `{ email, password }` | Вход; ставит cookies |
| `POST /tokens/access` | refresh-cookie | Новый access-токен и ротация refresh |
| `GET /tokens/refresh` | — | Статус refresh-токена (`{ tokenStatus, createdAt }`) |
| `GET /session` | — | Текущая авторизация (`{ authorization }`) |
| `DELETE /session` | — | Логаут, отзыв токенов |

`RegisterRequest`: `firstName`, `lastName`, `email`, `password`, опц. `capCode`
(при включённой капче), `externalId`, `userPlatformSource`.

### Пользователи (`/users`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `GET /profile` | — | Профиль (`UserInfoResponse`) |
| `PUT /profile` | `{ firstName, lastName, middleName, additionalInfo }` | Обновление профиля |
| `POST /password-resets` | `{ email }` | Запрос сброса пароля (письмо с кодом) |
| `PUT /password-resets/{code}` | `{ password }` | Установка нового пароля |

### Email (`/email`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /confirmations/{token}` | — | Подтверждение email по коду |
| `GET /info` | — | Текущий уровень email-оповещений (`{ muteLevel }`) |
| `DELETE /notifications/{event}` | — | Отписка от события (`NotificationEvent`) |
| `POST /codes?capId=` | опц. `capId` | Отправка кода подтверждения email |

### Порталы (`/portals`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /` | `{ name, description }` | Создание портала → `{ id }` |
| `GET /` | `page, size, sortBy, order` | Порталы владельца |
| `GET /shared` | `page, size, sortBy, order` | Доступные общие порталы |
| `GET /ids` | — | ID доступных порталов |
| `GET /info` | — | Краткая информация по доступным порталам |
| `GET /{portalId}` | — | Информация о портале |
| `PUT /{portalId}` | `{ name, description }` | Переименование/описание |
| `GET /shared/{portalId}` | — | Настройки: участники + `isPublic` |
| `PUT /shared/{portalId}/users` | `{ userIds: [...] }` | Замена списка участников |
| `GET /shared/{portalId}/visibility` | — | Текущая публичность |
| `PUT /shared/{portalId}/visibility` | `{ isPublic }` | Смена публичности |
| `DELETE /?ids=1,2` | `ids` | Удаление порталов → `{ count, deletedIds }` |

`PortalModel`-контракт: `PortalResponse` (`id, name, description, createdAt`),
`PortalInfoResponse` (`id, name, description`), `PortalSettingsResponse`
(`users: [{id, firstName, lastName, middleName, email}], isPublic`).

### Передача владения и история (`/portals/{id}/...`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `POST /{id}/owner-transfer` | `{ email, password, reason, keepOldOwnerAsMember }` | Инициировать передачу |
| `GET /{id}/owner-transfer` | — | Активный запрос (владелец или предлагаемый) |
| `POST /{id}/owner-transfer/confirm` | `{ password, reason }` | Подтвердить передачу |
| `POST /{id}/owner-transfer/reject` | `{ reason }` | Отклонить |
| `POST /{id}/owner-transfer/cancel` | — | Отозвать до решения |
| `GET /{id}/history?page=&size=` | — | История портала (`PageResponse<PortalHistoryResponse>`) |

Активный запрос живёт 72 часа и закрывается лениво как `EXPIRED`; одновременно
на портал может быть только один запрос (второй — `409`). Статусы:
`PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`. При подтверждении
владелец меняется, новый владелец убирается из участников, старый при
`keepOldOwnerAsMember=true` остаётся участником.

История (`portal_history`) хранит события передачи (`TRANSFER_*`) и изменения
портала (`NAME_CHANGED`, `DESCRIPTION_CHANGED`, `VISIBILITY_CHANGED`,
`USERS_CHANGED`, `PORTAL_DELETED`); доступ — участникам портала.

### Заявки

Портальные эндпоинты (`/portals/{portalId}/tickets`):

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /portals/{portalId}/tickets` | `{ title, body, ticketPriority, ticketAccessStatus }` | Создание заявки → `{ id }` |
| `GET /portals/{portalId}/tickets` | `status, page, size, sortBy, order` | Заявки портала |
| `GET /portals/{portalId}/tickets/ids` | `status, noAnswer` | ID заявок (фильтры) |
| `GET /portals/{portalId}/tickets/{ticketId}` | — | Заявка по ID |
| `PUT /portals/{portalId}/tickets/{ticketId}/status` | `{ status }` | Смена статуса |
| `PUT /portals/{portalId}/tickets/{ticketId}/priority` | `{ priority }` | Смена приоритета |
| `DELETE /portals/{portalId}/tickets/{ticketId}` | — | Удаление |

Поиск (`/tickets`):

| Метод и путь | Параметры | Назначение |
|---|---|---|
| `GET /tickets` | `search`, `startDate`, `endDate`, `status`, `priority`, `mine`, `page, size, sort` | Поиск заявок (`TicketResponse`) |
| `GET /tickets/mine` | `page, size, sortBy, order` | Мои заявки (`TicketResponseOld`) |

`TicketResponse`: `id, title, authorFullName, portalName, createdAt, portalId`.
`TicketResponseOld` дополнительно содержит `body`, `ticketPriority`,
`ticketStatus`, `ticketAccessStatus`.

`TicketPriority`: `CRITICAL, HIGH, MEDIUM, LOW, NONE`.
`TicketStatus`: `OPEN, IN_PROGRESS, CLOSED`.
`TicketAccessStatus`: `ALL_USERS, CREATOR_AND_PORTAL_USERS`.

### Сообщения (`/portals/{portalId}/tickets/{ticketId}/messages`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `POST .../messages` | `{ messageText }` | Создать сообщение → `{ id }` |
| `GET .../messages` | — | Сообщения заявки (`List<MessageResponse>`) |

### Вложения (`/portals/{portalId}/tickets/{ticketId}/attachments`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST .../attachments` | `multipart/form-data`: `file` + опц. `messageText` | Загрузка вложения |
| `GET .../attachments` | — | Список вложений |
| `GET .../attachments/{attachmentId}` | заголовок `Range` | Скачивание (поток с Range) |

`AttachmentResponse`: `id, messageId, ticketId, fileName, contentType,
sizeBytes, uploadedByFullName, createdAt, downloadUrl`.

### Оповещения (`/notifications`)

| Метод и путь | Параметры / тело | Назначение |
|---|---|---|
| `GET /notifications` | `page, size, unreadOnly` | Список оповещений |
| `GET /notifications/unread-count` | — | Счётчик непрочитанных `{ count }` |
| `GET /notifications/wait` | `afterId, timeoutSec, size` | Long polling (лимит `notifications-wait`) |
| `PUT /notifications/{id}/read` | — | Прочитано (гасит группу повторов) |
| `PUT /notifications/read-all` | — | Прочитать всё |
| `GET /notifications/preferences` | — | Включённые события и повторы |
| `PUT /notifications/preferences` | `{ events, repeatEnabled, repeatIntervalMinutes }` | Настройка событий/повтора |

Получатели — владелец портала и участники (`allowedUserIds`); автор действия
уведомление о своём действии не получает. `NEW_TICKET` и `NEW_MESSAGE`
повторяются, пока не прочитаны (по умолчанию каждые 30 минут). `wait`
возвращает страницу оповещений новее `afterId` либо пустую по таймауту
(по умолчанию 25 c, максимум 30).

`NotificationEvent`: `NEW_TICKET, NEW_MESSAGE, NEW_SYSTEM_MESSAGE,
NEW_TICKET_OR_MESSAGE, NEW_PORTAL, CHANGE_TICKET, RECOVERY_PASSWORD,
TICKET_DELETED, PORTAL_DELETED, PORTAL_TRANSFER_*, NONE`.

## Рейт-лимиты

Счётчик ведётся по IP и эндпоинту; при превышении — `429` с `Retry-After`.
Пороги заданы в `docker/app.env` (`RATE_LIMIT_<KEY>_REQUESTS` /
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
два порога в `docker/app.env`. Счётчики хранятся в памяти и сбрасываются при
рестарте. За прокси задайте `TRUST_PROXY_HEADERS: "true"`, иначе все запросы
будут считаться с одного IP.

## Тесты и проверки

Тесты выполняются на хосте, без Docker (собственная конфигурация в
`src/test/resources/application.properties`).

| Команда / файл | Что делает |
|---|---|
| `make test` | Юнит-тесты и срезы (`@DataJpaTest` на H2) |
| `make verify` | То же + интеграционные `*IT` (failsafe, GreenMail) |
| `make format` | Форматирование (`spotless:apply`) |
| `make check` | Проверки стиля (`spotless:check`, `checkstyle`) |
| `postman_collection.json` | Коллекция для импорта в Postman |

E2E-сценарии по живому стеку (`make up`) — через `make e2e-all` или отдельно:
`e2e-portal-access`, `e2e-ratelimit`, `e2e-jwt`, `e2e-notifications`,
`e2e-portal-transfer` (`scripts/e2e-*.sh`). Скрипты создают тестовых
пользователей и тратят рейт-лимиты — при повторных прогонах может
потребоваться `make restart`.

## Документация

- [`docs/jwt-audit.md`](docs/jwt-audit.md) — аудит JWT-аутентификации.
- [`sec.md`](sec.md) — безопасность сборки, конфигурации и контейнера.
- [`docs/code-style.md`](docs/code-style.md) — стиль кода.
- [`notActiv.md`](notActiv.md) — отчёт по мёртвому коду и коду совместимости.
