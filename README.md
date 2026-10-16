# RealHelpDesk API

REST API системы управления заявками предоставляет функционал для работы с порталами (тематическими
пространствами), заявками, сообщениями и вложениями.
Пользователи могут создавать и управлять порталами, формировать в них заявки, добавлять к заявкам
сообщения и прикреплять файлы-вложения.

## Возможности

- Регистрация, авторизация, аутентификация, верификация email, восстановление пароля.
- Порталы: создание, редактирование, удаление; публичный доступ и доступ по
  списку доверенных пользователей.
- Заявки: создание, статусы, приоритеты, удаление, поиск по заявкам.
- Формирование сообщений, прикрепление к заявке файловых вложений (MinIO, отдача с поддержкой HTTP `Range` для не стабильных сетей).
- Передача права владения порталом с подтверждением и полной историей состояний портала.
- Оповещения: email и in-app (SSE-канал, long polling — фолбэк, [как это работает](#оповещения-sse-и-long-polling)),
  настройки триггеров и повторов оповещений.
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

Полная структура пакетов (`src/main/java/com/ukhanov/realhelpdesk`):

```
com.ukhanov.realhelpdesk
├── core                          — общее для всех областей
│   ├── annotation                — @NoHtml: запрет HTML-разметки в строках
│   ├── config                    — Security/CORS/JWT, ClientIp, actuator, ProdPreflightValidator
│   ├── controller                — GET /api/v1/health
│   ├── exception                 — ApiException и GlobalExceptionHandler → RFC 7807 problem+json
│   ├── filter                    — JwtAuthFilter, CsrfProtectionFilter, LoggingFilter (MDC clientIp)
│   ├── http                      — ClientIpResolver, ETag, Range
│   ├── log                       — LogSanitizer
│   ├── mail                      — письма: config, controller, dto, exception, health, model, repository, service
│   ├── observability             — метрики и трассировка (Micrometer, OpenTelemetry)
│   ├── pagination                — единый контракт page/size/sortBy/order → PageResponse
│   ├── security
│   │   ├── accesscontrol         — AccessValidationService, TicketAccessValidationService
│   │   ├── auth                  — register/login/logout/refresh/tokens: JWT в http-only cookies
│   │   ├── captcha               — Proof-of-Work + эмодзи-эскалация, риск-движок по IP
│   │   ├── handler               — 401/403 в problem+json
│   │   ├── limiter               — квоты пользователя
│   │   ├── ratelimit             — аннотация @RateLimit и счётчики по IP
│   │   └── user                  — CurrentUserProvider, UserModel, роли и статусы
│   └── storage                   — MinIO: бакет, отдача с HTTP Range, health
├── domain                        — JPA-сущности, репозитории и доменные правила
│   ├── portal                    — портал: владелец, allowedUserIds, публичность (model, repository, service)
│   ├── ticket                    — заявки: статусы, приоритеты, политика TicketAccessStatus (model, repository, service)
│   ├── message                   — сообщения заявок (model, repository, service)
│   ├── attachment                — метаданные вложений (model, repository, service)
│   └── notification              — строки in-app оповещений и настройки пользователя (model, repository)
└── feature                       — REST-слой по областям; в каждом модуле:
    │                             controller, dto, exception, mapper, service
    ├── portalmanager             — порталы, настройки, история, передача владения
    ├── ticketmanager             — заявки: создание, поиск, статусы, приоритеты
    ├── messagemanager            — чтение и отправка сообщений
    ├── attachmentmanager         — загрузка и отдача вложений
    ├── notificationmanager       — оповещения: SSE, long polling, настройки и повторы
    └── usermanager               — профиль и пароли пользователей
```

Ошибки HTTP-слоя централизованно преобразуются в RFC 7807-подобные ответы
`GlobalExceptionHandler`. Правила доступа к порталам и заявкам — в разделе
[«Правила доступа к порталам и заявкам»](#правила-доступа-к-порталам-и-заявкам).

## Быстрый старт

Проект запускается в Docker; Java 21 и Maven нужны только для разработки
(тесты и сборка). Понадобятся Docker с Compose v2 и GNU Make —
`make help` печатает список всех команд.

1. **Создать файл настроек.** Первый раз:

   ```bash
   cp infrastructure/.env.example infrastructure/.env
   ```

   Или сразу `make env`: создаст файл из шаблона, а существующему допишет
   недостающие ключи.

2. **Заполнить секреты** в `infrastructure/.env` — это пустые строки шаблона,
   без них стек не поднимется:

   | Секрет | Чем заполнить |
   |---|---|
   | `JWT_SECRET` | случайная строка от 32 символов |
   | `CAPTCHA_HMAC_SECRET` | случайная строка от 32 символов |
   | `DB_PASSWORD` | любой пароль PostgreSQL |
   | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | учётная запись MinIO |
   | `METRICS_PASSWORD` | любой непустой пароль сервисной учётки скрейпа |

   `KEY_STORE_PASS` в шаблоне уже имеет локальное значение, `MAIL_PASSWORD`
   нужен только для внешнего SMTP (раздел «Почта»). Все остальные настройки
   уже содержат рабочие дефолты для локальной разработки.

3. **Поднять стек** — на выбор:

   - `make up` — в фоне: сборка образа и запуск всего
     (`docker compose up --build -d`, профили `infra`+`dev`+`app`);
   - `make dev-run` — в foreground: логи всех сервисов одним потоком,
     `Ctrl+C` — остановка.

4. **Проверить, что работает:**

   - `curl -sk https://localhost:8443/api/v1/health` → `200` — приложение живо;
   - `https://localhost:8443/swagger-ui.html` — Swagger UI
     (машинная спека — `GET /v3/api-docs`);
   - dev-сервисы (в рабочем контуре их нет): smtp4dev — тестовый почтовый
     ящик на `http://localhost:3000` (письма с кодами, зачем — раздел
     «Почта»), PostgreSQL `127.0.0.1:5432` и MinIO `127.0.0.1:9000` — порты
     публикует `db-gateway` профилем `dev`, сама БД наружу не проброшена.

5. **Попробовать API** через Swagger: зарегистрируйте пользователя и
   подтвердите email — письмо с кодом придёт в smtp4dev. Пока email не
   подтверждён, создание портала отвечает `403`.

6. **Работать с кодом:**

   - `make test` / `make verify` — юнит- и интеграционные тесты
     (поднятый стек не нужен);
   - `make format` / `make check` — форматирование и проверки стиля;
   - `make e2e-*` — e2e-сценарии на поднятом стеке
     (например, `make e2e-notifications`), `make e2e-all` — все сразу;
   - `make logs` — логи приложения, `make ps` — статус контейнеров,
     `make restart` — перезапуск приложения, `make down` — остановить стек
     (тома с данными сохраняются, `make down-volumes` — удалить).

## Конфигурация

**Как это устроено.** Приложение не хранит значений в коде: каждая настройка —
это переменная окружения, обязательная и без дефолта. В
`src/main/resources/application.properties` и `application-domains.properties`
записана только «схема» — строки вида `${VAR}` без значения. Если переменная
не задана, приложение не стартует и прямо называет причину
(`Could not resolve placeholder '<VAR>'`), а не молча работает на чужих
значениях.

**Где живут значения** — три места:

| Файл | Что это | Когда трогать |
|---|---|---|
| `infrastructure/.env.example` | Шаблон из git: все несекретные переменные с рабочими дефолтами для локальной разработки (рейт-лимиты, капча, сроки JWT, почта, MinIO, домены) и пустые строки секретов-напоминаний. Закомментированная секция «Рабочий контур» — значения для продакшена | Менять дефолт для всех разработчиков, добавлять новую несекретную переменную |
| `infrastructure/.env` | Ваш рабочий файл, в git не попадает. `make env` создаёт его из шаблона, существующему дописывает недостающие ключи. Его читают и compose (интерполяция `${VAR}`), и приложение (`env_file`) | Локальные значения и секреты: `JWT_SECRET`, `DB_PASSWORD`, `KEY_STORE_PASS`, `CAPTCHA_HMAC_SECRET`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `METRICS_PASSWORD`, `MAIL_PASSWORD` |
| `services.app.environment` в `infrastructure/docker-compose.yaml` | Мост compose → приложение: секреты подставляются как `${VAR:?}` — пустой или отсутствующий секрет валит запуск; сюда же вынесены динамические ключи с дефолтами (`OTLP_TRACING_ENDPOINT`, `TRUST_PROXY_HEADERS`, `CLIENT_IP_FORWARDED_INDEX`) | Добавлять новую секретную переменную |

**Как поменять настройку:**

1. Найдите `${VAR}` в `src/main/resources/application.properties`
   (или `application-domains.properties`).
2. Задайте значение: в `infrastructure/.env` — для себя локально;
   в `infrastructure/.env.example` — постоянный общий дефолт;
   в `services.app.environment` compose — новый секрет.
3. Примените: `make restart`.

Каждая переменная схемы объявлена ровно в одном месте —
`.env.example` (несекретные) или `services.app.environment` compose
(секреты и динамические ключи); парность сверяет `ApplicationConfigSchemaTest`
(`make test`).

Один compose-файл (`infrastructure/docker-compose.yaml`) делится профилями
`infra`/`dev`/`app`/`monitoring`/`load` — набор задаёт Makefile, различия
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

## Правила доступа к порталам и заявкам

Все проверки выполняются на сервере в момент обработки запроса: на методах
контроллеров стоит `@PreAuthorize`, который вызывает бин
`AccessValidationService` (порталы) или `TicketAccessValidationService`
(заявки, сообщения, вложения). Отказ — `403` в формате problem+json
(`GlobalExceptionHandler`), отсутствующая или чужая сущность — `404`.
Списки и массовые операции дополнительно фильтруются в сервисах: чужие
записи в ответ просто не попадают.

### Уровни доступа к порталу

| Уровень | Кто это | Как получает |
|---|---|---|
| Владелец | один пользователь портала | создал портал или подтвердил передачу владения |
| Доверенный | пользователи списка `allowedUserIds` | добавлен владельцем: `PUT /{portalId}/settings/users` |
| Публичный | любой вошедший пользователь | владелец открыл портал: `PUT /{portalId}/settings/visibility` (`isPublic=true`) |

Публичный уровень даёт только права на чтение и создание заявок — прав
управления порталом он не даёт.

### Порталы: операции по уровням

| Операция | Владелец | Доверенный | Публичный |
|---|:-:|:-:|:-:|
| Просмотр портала `GET /{portalId}`; списки доступных `/accessible`, `/ids`, `/info` | ✓ | ✓ | ✓ |
| Создание и просмотр заявок, чтение и отправка сообщений, вложения (по политике заявки — ниже) | ✓ | ✓ | ✓ |
| Редактирование `PUT`/`PATCH /{portalId}`, история `GET /{portalId}/history`, настройки `GET /{portalId}/settings` | ✓ | ✓ | — |
| Видимость `GET`/`PUT /{portalId}/settings/visibility`, участники `PUT /{portalId}/settings/users` | ✓ | — | — |
| Удаление `POST /portals/bulk-delete`, передача владения | ✓ | — | — |

Дополнительно:

- `GET /portals` отдаёт только свои порталы (где пользователь — владелец);
  `/accessible`, `/ids`, `/info` — все доступные, включая публичные.
- Новый портал создаёт любой вошедший пользователь (`POST /portals`), но
  только с **подтверждённым email** — иначе `403` «Подтвердите ваш адрес
  электронной почты».
- Удаление только массовое (`POST /portals/bulk-delete`): каждый портал
  удаляется своим владельцем, чужие пропускаются; в ответе — сколько
  удалено и какие id.

### Заявки: две политики доступа

Политику выбирает автор при создании заявки (`ticketAccessStatus` в запросе;
по умолчанию — `CREATOR_AND_PORTAL_USERS`):

| Политика | Кто читает заявку, её сообщения и вложения |
|---|---|
| `ALL_USERS` | все, кому доступен портал (включая публичного) |
| `CREATOR_AND_PORTAL_USERS` | владелец портала, доверенные и автор заявки |

Инвариант: заявка должна принадлежать порталу из URL — иначе `404`, даже
если id заявки известен (проверка `belongsToPortal`: владелец случайного
портала не откроет чужую заявку).

| Операция | Проверка | Кто может |
|---|---|---|
| Чтение заявки, сообщений, вложений (`GET ...`) | `hasTicketAccess` | по политике заявки (таблица выше) |
| Отправка сообщения, загрузка вложения (`POST ...`) | `hasTicketAccess` | тот же круг, что и чтение |
| Статус, приоритет, удаление заявки (`PUT .../status`, `PUT .../priority`, `DELETE ...`) | `hasTicketChange` | владелец портала, доверенные или автор заявки |

### Передача владения

- Инициирует (`POST /{portalId}/owner-transfer`) и отзывает
  (`POST /{portalId}/owner-transfer/cancel`) — только владелец;
- активный запрос видят только инициатор и предлагаемый владелец
  (`GET /{portalId}/owner-transfer`; чужим — `404`, чтобы не раскрывать
  факт передачи);
- подтверждает и отклоняет предлагаемый (`.../confirm`, `.../reject`):
  подтверждение требует пароля и активной учётной записи нового владельца,
  решение и причина фиксируются в истории портала и в оповещениях;
- после подтверждения прежний владелец, если запрос заложен с
  `keepOldOwner`, остаётся доверенным пользователем портала.

### Что проверяется до прав доступа

- Аутентификация: `JwtAuthFilter` отклоняет запрос без валидного JWT ещё
  до `@PreAuthorize` — см. [«Аутентификация»](#аутентификация).
- CSRF: изменяющие запросы обязаны нести заголовок `X-Requested-With` —
  см. [«Защита от CSRF»](#защита-от-csrf).
- Капча: регистрация и повторная отправка кода на email проходят проверку
  PoW/эмодзи — см. [«Капча»](#капча-proof-of-work--эмодзи-эскалация).

## Оповещения: SSE и long polling

In-app оповещения доходят по двум каналам, у обоих общий курсор `afterId`
(id последнего полученного оповещения), `size` ≤ 50 и
`Cache-Control: no-store`. Основной канал — SSE, long polling оставлен
фолбэком.

**Основной канал — SSE** `GET /api/v1/notifications/stream?afterId=0&size=10`.

Клиент держит одно долгоживущее соединение через `EventSource`:

- первым пакетом идёт комментарий `: connected` — клиент и прокси сразу
  видят, что поток живой; без него заголовки уходят только с первым
  оповещением и запрос выглядит висящим;
- события приходят как `event: notification` с телом оповещения в JSON;
- при подключении сразу отдаётся backfill — всё, что новее курсора, в
  порядке возрастания id; дальше — только новое, повторных выдач нет:
  курсор ведёт подписка и растёт с каждым отправленным оповещением;
- канал живёт 30 минут, дальше `EventSource` переподключается сам; разрыв,
  таймаут и ошибка снимают подписку из реестра ожидающих.

**Фолбэк — long polling** `GET /api/v1/notifications/wait?afterId=0&timeoutSec=25&size=10`.

- отвечает сразу, если есть оповещения новее курсора; иначе держит запрос до
  `timeoutSec` (25 с по умолчанию, 1..30) и возвращает пустую страницу —
  клиент тут же повторяет запрос;
- у эндпоинта свой рейт-лимит `notifications-wait` (делится на IP между
  вкладками), у SSE отдельного лимита нет — соединение открывается один раз;
- канал нужен, когда `EventSource` недоступен (не-браузерные клиенты) или
  прокси рвёт долгоживущие соединения: оба канала отдают одни и те же
  строки.

**Как устроено пробуждение.** Оба канала регистрируются в одном in-memory
`NotificationWaitRegistry` — список «пользователь → ожидающие колбэки» на
инстанс приложения. `NotificationPublisher` сохраняет оповещение в БД и
будит ожидающих пользователя **после коммита** (`wakeAfterCommit`): раньше
перечитывать БД нельзя — запись ещё не видна. Пробуждение одноразовое,
реестр очищается:

- long-poll колбэк перечитывает БД и резолвит запрос, только если строки
  появились; иначе клиент продолжает ждать до таймаута;
- SSE-подписка отправляет пакеты и, пока канал открыт, заново встаёт в
  реестр;

колбэки переносят снимок MDC контекста запроса — их будят поток публикации
и планировщик повторов, которым этот контекст не принадлежит.

Покрытие: `NotificationManageServiceTest` (SSE и общий wake с long polling),
`NotificationWaitRegistryTest` (реестр), `NotificationControllerTest`
(контракт `/stream`), `NotificationPublisherTest` (будка после коммита),
`make e2e-notifications` (SSE и long polling по HTTP).

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

