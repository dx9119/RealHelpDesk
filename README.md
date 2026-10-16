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
- Оповещения: email и in-app (SSE-канал, long polling — фолбэк), настройки триггеров и повторов оповещений.
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

API — `https://localhost:8443`. Вспомогательные сервисы dev-среды (в рабочем
контуре их нет): smtp4dev — тестовый почтовый ящик (`http://localhost:3000`,
зачем он нужен — в разделе «Почта»), PostgreSQL (`127.0.0.1:5432`), MinIO
(`127.0.0.1:9000`) — оба порта публикует `db-gateway` (профиль `dev`), сама БД
наружу не проброшена. Полный список команд — `make help`.

## Конфигурация

Схема приложения (`src/main/resources/application.properties`,
`application-domains.properties`) содержит только обязательные `${VAR}` без
дефолтов: отсутствующая переменная валит старт с
`Could not resolve placeholder '<VAR>'` — тихих подстановок нет. Значения
живут в трёх местах:

| Файл | Что там |
|---|---|
| `infrastructure/.env.example` | Шаблон единственного env-файла: все несекретные значения с безопасными дефолтами (рейт-лимиты, клиентский IP, капча, сроки JWT, почта, MinIO, домены) и пустые строки секретов. Локальные значения активны по умолчанию; различия рабочего контура — секция «Рабочий контур» (закомментирована) |
| `infrastructure/.env` | Рабочий файл (в git не попадает): создаётся `make env` из шаблона (существующий дополняется недостающими ключами). Служит одновременно `env_file` приложения и источником `${VAR}`-интерполяции compose. Секреты: `JWT_SECRET`, `DB_PASSWORD`, `KEY_STORE_PASS`, `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` |
| `services.app.environment` в `infrastructure/docker-compose.yaml` | Секреты из `.env` (`${VAR:?}` — пустой или отсутствующий секрет валит запуск) и динамические ключи с дефолтами (`OTLP_TRACING_ENDPOINT`, `TRUST_PROXY_HEADERS`, `CLIENT_IP_FORWARDED_INDEX`) |

Каждая переменная схемы объявлена ровно в одном из этих мест — парность
сверяет `ApplicationConfigSchemaTest` (`make test`). Один compose-файл
(`infrastructure/docker-compose.yaml`) делится профилями
`infra`/`dev`/`app`/`monitoring`/`load` — наборы задаёт Makefile, различия
сред живут в `.env`, а не в отдельных compose-файлах.

### Клиентский IP

Логи (MDC `clientIp`) и ключ рейт-лимита получают адрес посетителя из одного
резолвера (`ClientIpResolver`), настройки — в `infrastructure/.env`
(`CLIENT_IP_HEADERS`; `TRUST_PROXY_HEADERS` и `CLIENT_IP_FORWARDED_INDEX`
задаёт compose-интерполяция с дефолтами `false`/`0`):

| Переменная | Назначение |
|---|---|
| `TRUST_PROXY_HEADERS` | `false` (по умолчанию) — всегда адрес сокета запроса; `true` — из заголовков прокси. Включать только за прокси: иначе клиент присылает свой заголовок и подставляет чужой IP в логи и в счётчик лимита |
| `CLIENT_IP_HEADERS` | Заголовки через запятую в порядке приоритета, берётся первый пригодный: `X-Forwarded-For`, `X-Real-IP` (nginx), `CF-Connecting-IP` (Cloudflare), `True-Client-IP`, `X-Client-IP`, `Proxy-Client-IP`/`WL-Proxy-Client-IP` (WebLogic), `Forwarded` (RFC 7239, `for=1.2.3.4;proto=https`) |
| `CLIENT_IP_FORWARDED_INDEX` | Какой элемент списка в значении брать: `0` — первый слева (адрес клиента), отрицательный — справа (`-1` — ближайший прокси). За цепочкой из нескольких прокси ставьте `-1`: слева в `X-Forwarded-For` пишет клиент |

### Профили

Профиль задаётся `SPRING_PROFILES_ACTIVE`: по умолчанию в шаблоне
`infrastructure/.env.example` — `debug` (локальная разработка), рабочий
контур — `prod` (секция «Рабочий контур» в `infrastructure/.env`).
Профили меняют уровни логирования; `prod` дополнительно закрепляет инварианты
(SQL-трассировка выключена, миграции включены, тексты исключений скрыты),
а `ProdPreflightValidator` останавливает старт рабочего контура с
отладочной конфигурацией (выключенная капча, localhost в CORS,
самоподписанный keystore, почта на локальном SMTP):

| Профиль | Файл | Что включает |
|---|---|---|
| `debug` | `application-debug.properties` | Локальная отладка: DEBUG по приложению, SQL-логи Hibernate без значений параметров |
| `prod` (рабочий контур) | `application-prod.properties` | Рабочий контур: INFO/WARN, пошаговая трассировка и SQL выключены, инварианты рабочего контура |

### Почта

Почта уходит во внешний SMTP-сервер: встроенного SMTP нет. В dev-среде при
этом работает smtp4dev — тестовый SMTP-сервис (контейнер `smtp`, UI на
`http://localhost:3000`), который поднимается только профилем `dev`
(`make up`); в рабочем контуре (`make up-prod`, профили `infra`+`app`) его нет.

**Зачем в проекте smtp4dev.** Регистрация, подтверждение email и восстановление
пароля отправляют письма с кодами, а в dev/e2e их надо прочитать — иначе
сценарий не пройдёт дальше формы. smtp4dev принимает всё, что шлёт приложение,
и отдаёт письма через UI и REST API: e2e-скрипты
(`scripts/e2e-jwt-flow.sh`, `scripts/e2e-portal-transfer.sh`) забирают коды
сброса и подтверждения именно оттуда. Роли распределены так: в рабочем контуре
почтой занимается внешний SMTP-сервер, в интеграционных тестах (`mvn verify`) —
GreenMail, smtp4dev — только dev-среда и e2e-сценарии на ней: он не входит ни
в профили рабочего контура, ни в образ приложения.

Настройки — `spring.mail.*` в `infrastructure/.env` (шаблон: локальные
значения активны, рабочий SMTP — секция «Рабочий контур»), пароль — секрет
`MAIL_PASSWORD` там же:

| Переменная | Назначение |
|---|---|
| `MAIL_PROTOCOL` / `MAIL_HOST` / `MAIL_PORT` | Транспорт и адрес SMTP-сервера. В секции «Рабочий контур» — внешний хост, порт `587`, `SMTP_STARTTLS=true` |
| `MAIL_USER` / `MAIL_PASSWORD` | Учётная запись SMTP. `MAIL_PASSWORD` — секрет в `infrastructure/.env`; локальному smtp4dev пароль не нужен (тогда `SMTP_AUTH=false`) |
| `SMTP_AUTH` / `SMTP_STARTTLS` | Аутентификация и STARTTLS; `SMTP_AUTH=true` без `MAIL_USER` — ошибка preflight'а |
| `MAIL_FROM` | Отправитель писем (`email.from`), например `noreply@example.com` |
| `MAIL_NOTIFY` | Адрес, куда падают уведомления о заявках (`email.notify`) |
| `APP_PROJECT_NAME` | Название проекта в письмах |

Таймауты соединения зашиты в схеме приложения (connect 3 с, ответ сервера и
запись 10 с) — зависший SMTP не держит ни отправку, ни health-проверку.

Контроль в рабочем контуре: `ProdPreflightValidator` не даст стартовать с
пустым `MAIL_HOST` или наведённым на локальный SMTP (`localhost`,
`127.0.0.1`, имя контейнера `smtp`), с `MAIL_PORT` вне диапазона 1–65535,
без `MAIL_FROM` и при `SMTP_AUTH=true` без `MAIL_USER`. Доступность сервера
отдаёт health-группа `smtp` (`/actuator/health/mail`, рукопожатие без
письма, кэш 5 с); она сознательно вне readiness — недоступная почта не
останавливает приём заявок, письма уходят после восстановления.

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
`jwt.cookie.domain` (`JWT_COOKIE_DOMAIN`).

### Защита от CSRF

Cookie уходят в том числе кросс-сайтово (`SameSite=None` по умолчанию), поэтому
изменяющие запросы обязаны нести заголовок `X-Requested-With` (любое непустое
значение) — иначе `403` в формате problem+json. Проверку делает `CsrfProtectionFilter`;
браузер не добавит кастомный заголовок без preflight, а preflight чужого origin
отклоняет CORS (allow-list `CORS_ALLOWED_ORIGINS`). Для односайтового фронта
дополнительно достаточно `JWT_COOKIE_SAMESITE=Lax`.

### Капча (Proof-of-Work + эмодзи-эскалация)

Локально выключена (`CAPTCHA_ENABLED=false` — дефолт шаблона), в рабочем
контуре включена обязательно (prod-preflight валит старт без неё). Вместо
картинки с текстом (устойчивой к OCR/LLM) — двухуровневая схема:

1. `POST /api/v1/captcha/challenge { action }` отдаёт подписанное задание:
   базовый уровень — PoW (соль + сложность `CAPTCHA_POW_DIFFICULTY`, по
   умолчанию 18 — доли секунды на телефоне);
2. клиент подбирает `nonce`: ведущие нулевые биты `SHA-256("salt:nonce")`
   ≥ сложности — виджет `GET /captcha/widget.js` делает это в Web Worker,
   `await window.RealHelpCaptcha.getToken(action)` возвращает `captchaToken`;
3. `POST /api/v1/captcha/solve` обменивает решение на подписанный токен —
   он и предъявляется на регистрацию (поле `captchaToken`) или в заголовке
   `X-Captcha-Token` на `POST /email/codes`.

**Эскалация по риску** (локальный риск-движок, без внешних провайдеров):
провалы капчи считаются по IP за окно (`CAPTCHA_RISK_FAILURE_THRESHOLD=5`
за `CAPTCHA_RISK_FAILURE_WINDOW_SECONDS=600`); набравший порог получает
вместо PoW эмодзи-задание — сетка 3×3 глифов и выбор ячеек в оверлее
виджета (`prompt`/`cells` в challenge, ответ — HMAC в подписи, kill-switch
`CAPTCHA_EMOJI_ENABLED=false`). Успех сбрасывает счётчик IP.

Защита от автоматики: подписи HMAC (`CAPTCHA_HMAC_SECRET` — секрет в
`infrastructure/.env`, минимум 32 символа), одноразовость challenge и токена,
минимальное время заполнения формы (`CAPTCHA_MIN_FILL_SECONDS`, 0 локально /
3 в рабочем контуре) и honeypot-поле `website` (его заполнение = `honeypot`
в ответе). При отказе в проблеме-JSON поле `code` — `pow_failed`,
`wrong_answer`, `replayed`, `too_fast`, `honeypot` и т.д. (полный список —
`docs/api.md`). Метрики: `captcha.challenge.issued{type}` и
`captcha.verification{type,result,reason}` (`type` — доля эскалации).
Сроки: `CAPTCHA_CHALLENGE_TTL_SECONDS` / `CAPTCHA_TOKEN_TTL_SECONDS`
(по умолчанию 300 c). Внешние провайдеры (Turnstile/SmartCaptcha/hCaptcha)
из реализации исключены оконательно — см. [`docs/capcha.md`](docs/capcha.md).

## API

Описание всех эндпоинтов — [`docs/api.md`](docs/api.md). Машинная спека собирается из
контроллеров: `GET /v3/api-docs`, UI — `https://localhost:8443/swagger-ui.html`
(`make openapi` кладёт спеку в `openapi.json`, `make openapi-types` генерирует
TypeScript-типы для фронта).

Соглашения контракта: единый стиль пагинации `page/size/sortBy/order` (`page` с нуля,
`size` ≤ 100), коллекции без пагинации в обёртке `{ items, total }`, `ETag`/`If-Match`
(`412` на устаревшую версию) и `PATCH` для частичных обновлений, оповещения по SSE
(`GET /notifications/stream`).

## Рейт-лимиты

Счётчик ведётся по IP и эндпоинту; при превышении — `429` с `Retry-After`.
Пороги заданы в `infrastructure/.env` (`RATE_LIMIT_<KEY>_REQUESTS` /
`RATE_LIMIT_<KEY>_WINDOW_SECONDS`).

| Ключ | Эндпоинт | Лимит |
|---|---|---|
| `captcha-challenge` | `POST /captcha/challenge` | 30 / 60 c |
| `captcha-solve` | `POST /captcha/solve` | 30 / 60 c |
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
два порога в `infrastructure/.env`. Счётчики хранятся в памяти и сбрасываются при
рестарте. За прокси задайте `TRUST_PROXY_HEADERS=true` в `infrastructure/.env`
(см. «Клиентский IP»), иначе все запросы будут считаться с одного IP.

## Наблюдаемость

Метрики (Micrometer → экспозиция Prometheus-формата, хранение в Mimir), трейсы
(OTel → Tempo) и логи с `traceId`/`spanId` (→ Loki через Alloy). Стек поднимается
вместе с приложением:

| Команда | Что делает |
|---|---|
| `make obs-up` | Стек + Mimir (`127.0.0.1:9090`, Prometheus-совместимый API) + Grafana (`127.0.0.1:3001`) + Tempo/Loki/Collector |
| `make obs-down` | Остановить наблюдаемость (тома целы) |
| `make obs-open` | Открыть Grafana в браузере |
| `make obs-reload` | Перечитать правила алертов Mimir (рестарт контейнера) |
| `make obs-alerts` | Текущие состояния алертов |

Метрики на management-порту 8081 (`/actuator/prometheus`, HTTP Basic). Сводный
дашборд — Grafana → RealHelpDesk → Обзор; алерты (8 шт.) имеют runbooks в
`docs/runbooks/`. Как добавить метрику, правила кардинальности и отладка по
traceId — `docs/observability/overview.md`; каталог бизнес-метрик —
`docs/observability/metrics.md`; SLO — `docs/observability/slo.md`.

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
| `make openapi` | Снять OpenAPI-спеку с запущенного стека в `openapi.json` |
| `make openapi-types` | Сгенерировать TypeScript-типы (`npx openapi-typescript`) |
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

### Нагрузочное тестирование (k6)

Полный user journey под нагрузкой: каждый VU повторяет виток
«профиль → (ре)логин → портал → заявка → сообщение → список сообщений →
поиск → unread-count → long-poll уведомлений → refresh access»
с паузой `THINK_TIME` между итерациями. Скрипт —
`scripts/load/user-journey.js` (k6), обёртка — `scripts/load/run.sh`.

| Команда | Что делает |
|---|---|
| `make load` | Полный прогон: сидит пул пользователей и гоняет journey (10 VU, 5 минут) |
| `make load-smoke` | Короткий smoke: 2 VU, 1 минута |
| `make up-load` | Только поднять стек с load-оверлеем (k6 запускать вручную) |

Параметры: `TARGET_VUS`, `DURATION`, `POOL_SIZE` (≤10), `THINK_TIME`,
`WAIT_TIMEOUT_SEC` — через окружение, например `TARGET_VUS=30 DURATION=15m make load`.
`RESET_LIMITS=1` — перезапустить app перед прогоном (обнуляет in-memory
счётчики рейт-лимитов, если предыдущий прогон исчерпал окно логина 10/300с).

**Как устроено:**

- **Пул пользователей** — пул до 10 `load-pool-<RUN_ID>-pN@load.test` сидирует
  `run.sh` (регистрация + psql-верификация email — портал создаёт только
  `isEmailVerified=true`). VU распределяют пользователей по кругу
  (`VU1 → p1, VU2 → p2, …`), общий лимит регистрации не превышается.
- **Рейт-лимиты** — `make up-load` передаёт `TRUST_PROXY_HEADERS=true`
  инлайном: каждая k6-VU шлёт
  уникальный `X-Forwarded-For` и получает свой bucket рейт-лимитов. Без
  этого профиля все VU делят один `127.0.0.1` и логин упирается в лимит
  10/300с. Включать **только локально** — за таким профилем нет прокси.
- **Совместимость с наблюдаемостью** — `up-load`/`load-smoke` передают
  `OTLP_TRACING_ENDPOINT` на collector инлайном: app пересоздаётся, и без
  этого ключа экспорт трейсов откатился бы на `localhost:4317`
  (дефолт compose-интерполяции), сломав связку с запущенным obs-стеком
  (`make obs-up`).
  Прогон метрик/трейсов во время load-теста: поднимите `make obs-up`,
  затем `make load-smoke` — смотрите Grafana и `make obs-alerts`.
- **Cookie jar** — явная per-VU jar (k6 сбрасывает встроенную между
  итерациями): access-token из логина живёт между витками, re-login
  происходит только по 401.
- **Капча** выключена локально (`CAPTCHA_ENABLED=false` в
  `infrastructure/.env`, дефолт шаблона) — иначе
  регистрация/логин под нагрузкой не пройдут.

**Пороги** (падают с nonzero exit — прогон считается проваленным):

| Порог | Значение |
|---|---|
| `http_req_failed` | <1% |
| `checks` | >99% |
| `http_req_duration{search}` p95 | <500ms |
| `http_req_duration{ticket_create}` p95 | <1200ms |
| `http_req_duration{notifications_wait}` p95 | <6s (long-poll 4s) |

**Эталонный результат** (10 VU, 5 минут, 669 итераций, локально):
99.8% checks зелёных, 0.4% HTTP-ошибок, ticket_create p95 ≈ 43ms,
search p95 ≈ 8ms, ~15 RPS.

Отчёт k6 (RPS, percentiles по endpoint'ам, пороги) печатается в stdout.

