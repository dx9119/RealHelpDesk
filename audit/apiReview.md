# apiReview.md — пригодность REST API для веб-клиента (React)

Аудит контракта REST API: 12 контроллеров, ~45 эндпоинтов, обработка ошибок, пагинация, аутентификация, CORS, загрузка/скачивание файлов, long polling.

Метод: чтение контроллеров, DTO и `GlobalExceptionHandler`, сверка с [docs/api.md](docs/api.md), плюс проверка поведения по исходникам Spring Framework / Spring Security (для CORS и 401/403 — эти решения принимает библиотека, а не код проекта).

Приоритеты: **P0** — кросс-доменный SPA работать не будет, **P1** — контракт мешает клиенту, **P2** — эргономика, **P3** — улучшение.

---

## 1. Вердикт

База для SPA **хорошая**: единый префикс `/api/v1`, `PageResponse` вместо сырых `Page`, ошибки в формате RFC 9457 (`application/problem+json`) с полями по конкретным полям формы, cookie-аутентификация с `HttpOnly`, `201 + Location` на создании, поддержка `Range` для файлов, `Retry-After` на 429, `Instant` в ISO-8601.

Но при выносе фронтенда на **отдельный origin** API не заработает: конфигурация CORS отклоняет предполётные запросы с заголовками, а всё, что нужно клиенту из заголовков ответа (маркеры истечения токена, имя файла, `Retry-After`), скрыто от JavaScript. Это два пункта P0 в §2.

| Категория | Оценка |
|---|---|
| Формат ошибок | ✅ RFC 9457 + `errors` по полям — годится для форм «из коробки» |
| Пагинация | ⚠️ три разных стиля параметров в одном API, `size` без верхней границы на одном эндпоинте |
| Аутентификация | ⚠️ cookie + `SameSite=None` + CSRF выключен; логика refresh есть, но её маркеры не видны клиенту |
| CORS | ⛔ предполётные запросы с `Content-Type: application/json` отклоняются; заголовки не экспонированы |
| Модель данных для UI | ⚠️ нет `authorId` — клиент не может отличить свои сообщения от чужих |
| Файлы | ⚠️ механика хорошая (Range, поток), но `Content-Disposition` не читается из JS |
| Real-time | ⚠️ long polling вместо SSE/WebSocket, лимит на IP |
| Документирование | ⚠️ есть ручной [docs/api.md](docs/api.md), нет OpenAPI-спеки |

---

## 2. P0 — блокеры для кросс-доменного клиента

### 2.1 CORS: предполётный запрос с заголовками отклоняется (403)

Конфигурация собрана вручную: `CorsConfiguration` с `allowedOrigins`, `allowedMethods`, `allowCredentials(true)`, `maxAge(3600)` — и **без `allowedHeaders`** ([AppConfig:57-68](src/main/java/com/ukhanov/realhelpdesk/core/config/AppConfig.java:57-L68)).

Что происходит по исходникам Spring:

1. `CorsConfiguration.checkHeaders(requestHeaders)`: если список запрошенных заголовков **непустой**, а `allowedHeaders` не задан — возвращается `null` («ни один заголовок не разрешён»).
2. `DefaultCorsProcessor.handleInternal(...)`: `if (preFlightRequest && allowHeaders == null) { rejectRequest(response); return false; }` → **`403 Invalid CORS request`**.

Браузер включает `Access-Control-Request-Headers` для любого не-«safelisted» заголовка. `Content-Type: application/json` — не safelisted, поэтому **любая JSON-мутация (`POST`/`PUT`) с другого origin падает на preflight**, ещё до контроллера.

| Запрос из React | Что будет сейчас |
|---|---|
| `GET /api/v1/portals` (`credentials: 'include'`) | ✅ работает (простой запрос, preflight не нужен) |
| `POST /api/v1/portals` с `Content-Type: application/json` | ⛔ `403 Invalid CORS request` на preflight |
| `DELETE /api/v1/auth/session` (preflight без `Access-Control-Request-Headers`) | ✅ проходит (метод `DELETE` разрешён) |
| Любой запрос с кастомным заголовком (`X-Request-ID`, который умеет читать `LoggingFilter`) | ⛔ `403` на preflight |

Симптом для команды: «логин работает, данные грузятся, но ничего не сохраняется».

**Исправление** — задать заголовки и явно экспонировать нужные (см. §8). `allowedHeaders = ["*"]` безопасен вместе с `allowCredentials`: Spring в этом случае подставляет заголовки из запроса (это описано в javadoc `setAllowedHeaders`), а не отдаёт литерал `*`.

**Проверка одной командой** (после правки и до неё):

```bash
curl -i -X OPTIONS 'https://localhost:8443/api/v1/portals' \
  -H 'Origin: https://front.example.com' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
# ожидается 200 + Access-Control-Allow-Origin + Access-Control-Allow-Headers: content-type
# сейчас ожидается 403 + "Invalid CORS request"
```

### 2.2 Заголовки ответа не экспонированы — клиент не видит главного

`exposedHeaders` не задан, а по спецификации CORS из ответа доступны JS только `Cache-Control`, `Content-Language`, `Content-Length`, `Content-Type`, `Expires`, `Last-Modified`, `Pragma`. Всё остальное скрыто. Что теряет клиент:

| Заголовок | Где отдаётся | Что теряет React |
|---|---|---|
| `X-Access-Token-Expired`, `X-Auth-Token-Missing`, `X-Malformed-Token`, `X-Invalid-Token`, `X-Verify-Token-Failed` — маркеры причины 401 | [JwtAuthFilter:115-122](src/main/java/com/ukhanov/realhelpdesk/core/filter/JwtAuthFilter.java:115-L122) | Не может отличить «токен истёк → тихо обновить» от «токена нет → на логин». Остаётся парсить русский `detail` из тела — хрупко |
| `Content-Disposition` | [AttachmentController:121-122](src/main/java/com/ukhanov/realhelpdesk/feature/attachmentmanager/controller/AttachmentController.java:121-L122) | Имя файла при скачивании известно только из DTO; при прямом `fetch` — недоступно |
| `Content-Range`, `Accept-Ranges` | [AttachmentController:106-127](src/main/java/com/ukhanov/realhelpdesk/feature/attachmentmanager/controller/AttachmentController.java:106-L127) | Нельзя вести докачку по `Range` и проверять размер фрагмента |
| `Retry-After` | [RateLimitInterceptor:73](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/interceptor/RateLimitInterceptor.java:73) | Нельзя корректно показать «повторите через N сек» — только текст из `detail` |
| `Location` | `POST` создания (201) | Нельзя прочитать URL созданного ресурса |

Отдельно ценно: маркеры в `JwtAuthFilter` — **хорошее проектное решение**, которое сейчас полностью невидимо кросс-доменному клиенту. Достаточно добавить их в `exposedHeaders`, и у SPA появится честная логика refresh без парсинга текста.

### 2.3 Cookie-аутентификация: требования к клиенту и CSRF

Токены — в `HttpOnly; Secure` cookie ([AuthController:122-132](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/AuthController.java:122-L132)), refresh-cookie ограничен путём `/api/v1/auth` (хорошо: не уходит на весь API). Следствия для клиента:

| Что | Требование |
|---|---|
| Любой запрос | `credentials: 'include'` в `fetch` (или `withCredentials: true` в axios), иначе cookie не отправятся |
| `SameSite=None` (значение по умолчанию в [stack.env:65](scripts/docker/stack.env:65)) | Обязателен `Secure` (есть) и **обязателен рабочий CORS** — см. §2.1. Фронт и API на разных сайтах — вариант по умолчанию |
| CSRF | `http.csrf(...disable())` ([WebSecurityConfiguration:54](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:54)) — защиты нет. Она держится только на том, что мутации требуют `Content-Type: application/json` (а он вызывает preflight) |

Уязвимая поверхность при `SameSite=None`: `POST`-эндпоинты **без тела** — их можно отправить кросс-сайтовой формой без preflight: `POST /api/v1/email/confirmations/{token}` ([EmailPolicyController:36-40](src/main/java/com/ukhanov/realhelpdesk/core/mail/controller/EmailPolicyController.java:36-L40)) и `POST /api/v1/email/codes?capId=...` (с параметрами в query). Последствия ограничены, но:

- для **односайтового** развёртывания (фронт и API на одном сайте) следует ставить `SameSite=Lax` — тогда CSRF закрывается браузером, и вся конструкция перестаёт зависеть от «JSON вызывает preflight»;
- для кросс-сайтового — добавить double-submit CSRF-токен или требовать кастомный заголовок (например, `X-Requested-With`) на всех мутациях: заголовок форсирует preflight, а CORS его отсечёт у чужого origin.

---

## 3. P1 — контракт данных, мешающий клиенту

| # | Проблема | Почему мешает React | Что сделать |
|---|---|---|---|
| 1 | **Два разных DTO для одной сущности**: `TicketResponse` (поиск) и `TicketResponseOld` (список, деталь, «мои заявки») — [TicketSearchController:48-57](src/main/java/com/ukhanov/realhelpdesk/feature/ticketmanager/controller/TicketSearchController.java:48-L57), [TicketController:55](src/main/java/com/ukhanov/realhelpdesk/feature/ticketmanager/controller/TicketController.java:55). Имя `...Old` попало в публичный контракт | Клиент вынужден держать два типа и две ветки рендера для одного списка заявок; «Old» в имени API — сигнал, что контракт не проектировался | Свести к одному `TicketResponse` (с `body`), поиск перевести на него же; переименовать до релиза, пока клиентов мало |
| 2 | **Нет идентификаторов пользователей в ответах**: `MessageResponse(id, messageText, authorFullName, ticketId, createdAt)` ([MessageResponse](src/main/java/com/ukhanov/realhelpdesk/feature/messagemanager/dto/MessageResponse.java:5)) — нет `authorId`; в `AttachmentResponse` только `uploadedByFullName`; в `TicketResponse` — `authorFullName` | Чат не может отрисовать «мои сообщения»: сравнивать по ФИО нельзя (однофамильцы, смена имени). Аватар/ссылка на профиль тоже невозможны | Добавить `authorId`/`uploadedById`/`authorId` в DTO |
| 3 | **`GET /auth/session` возвращает русский текст**: `{ "authorization": "Активен" }` ([AuthorizationResponse](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/dto/AuthorizationResponse.java:3), [AuthController:106-108](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/AuthController.java:106-L108)) | Локализованная строка как машинное поле; значение всегда `"Активен"` (иначе запрос не дошёл бы), то есть информации не несёт. При этом **id пользователя клиент не получает** — нужен второй запрос `GET /users/profile` на каждом старте приложения | Вернуть `{ authenticated: true, user: UserInfoResponse }` или хотя бы `userId` |
| 4 | **Три стиля пагинации в одном API**: `page,size,sortBy,order` (порталы, заявки, оповещения, история, `/tickets/mine`); `Pageable` → `page,size,sort=поле,напр` (`GET /tickets`); `page,size` без сортировки (`/history`) | Клиент пишет разные хелперы под каждый эндпоинт; сортировка `sort=createdAt,desc` и `sortBy=createdAt&order=desc` несовместимы | Выбрать один стиль (`sort`/`order` проще для React-таблиц) и привести все эндпоинты |
| 5 | **`size` без верхней границы на `GET /tickets`**: используется `Pageable` без `@Max` и без `spring.data.web.pageable.max-page-size` (в конфигурации ключа нет) — остальные эндпоинты ограничены `@Max(100)` | `GET /tickets?size=100000` выгрузит всю таблицу: и DoS-вектор, и рассинхрон поведения | Добавить `spring.data.web.pageable.max-page-size=100` и/или валидацию |
| 6 | **Пагинация 0-based, но нигде не задокументирована**: `PageResponse.page` начинается с 0 | Классическая ошибка клиента: «страница 1» покажет вторую | Зафиксировать в [docs/api.md](docs/api.md) и в OpenAPI; в идеале принимать 1-based и конвертировать |
| 7 | **`Location` ведёт на несуществующий маршрут**: `POST .../messages` → `/api/v1/portals/{p}/tickets/{t}/messages/{id}` ([MessageController:42](src/main/java/com/ukhanov/realhelpdesk/feature/messagemanager/controller/MessageController.java:42)), а `GET` отдельного сообщения в API нет | Клиент, следующий за `Location` (типовая практика после 201), получает 404 | Либо добавить `GET .../messages/{id}`, либо указывать в `Location` существующий ресурс (ленту сообщений заявки) |
| 8 | **Несогласованные коды на `PUT`**: `PUT /users/profile` → 200 + тело, `PUT /portals/{id}` → 200 + тело, `PUT /notifications/preferences` → 200 + тело, но `PUT /portals/shared/{id}/users`, `PUT /shared/{id}/visibility`, `PUT .../status`, `PUT .../priority`, `PUT /notifications/{id}/read` → 204 без тела | Клиент не может написать один обработчик мутаций: где-то нужно читать тело, где-то — нет | Договориться: «изменение ресурса → 200 + актуальный ресурс», «команда/флаг → 204» |
| 9 | **Массивы на верхнем уровне** (без обёртки): `GET /portals/ids` → `[1,2]`, `GET /portals/info` → `[...]`, `GET .../tickets/ids` → `[...]`, `GET .../messages` → `[...]`, `GET .../attachments` → `[...]` | Смешение стилей (объект vs массив) мешает единому слою API-клиента; массивы к тому же не расширяемы (нельзя добавить `total` без ломающего изменения) | Обернуть: `{ items: [...] }` или `PageResponse` |
| 10 | **Нет гарантированного порядка сообщений**: `MessageRepository.findByTicketId` — без `ORDER BY` ([MessageRepository:15-16](src/main/java/com/ukhanov/realhelpdesk/domain/message/repository/MessageRepository.java:15-L16)), в отличие от вложений (`ORDER BY a.id`) и оповещений (`ORDER BY id ASC`) | Лента чата может прийти в произвольном порядке (зависит от плана запроса) | Добавить `OrderByCreatedAtAsc`/`OrderByIdAsc` |
| 11 | **`POST /users/password-resets` → `202 Accepted`** ([UserController:50-57](src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/controller/UserController.java:50-L57)), хотя отправка письма синхронная; в [docs/api.md](docs/api.md) код не указан | Клиент по 202 решит, что письмо придёт «потом», и не покажет ошибку SMTP (а она вернёт 503) | Либо 200/204 как у остальных, либо честно сделать асинхронной и документировать |
| 12 | **Два способа получить «мои заявки»**: `GET /tickets?mine=true` (DTO `TicketResponse`) и `GET /tickets/mine` (DTO `TicketResponseOld`) | Дублирование с разными ответами; непонятно, какой использовать | Оставить один |

---

## 4. P2 — эргономика и устойчивость

| # | Проблема | Что предлагается |
|---|---|---|
| 13 | **Long polling вместо push**: `GET /notifications/wait` — `DeferredResult` с таймаутом 25 с ([NotificationController:55-62](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/controller/NotificationController.java:55-L62)) | Для React это рабочий, но худший вариант: висящий запрос, необходимость `AbortController` при размонтировании, `429` при нескольких вкладках (лимит 30/мин **на IP**, [stack.env:44-47](scripts/docker/stack.env:44-L47) — за NAT весь офис делит один лимит), и повторная аутентификация на ASYNC-dispatch. Рассмотреть SSE (`SseEmitter`/WebFlux) или WebSocket для канала оповещений; long polling оставить фолбэком |
| 14 | **Нет OpenAPI/JSON Schema**: контракт живёт в [docs/api.md](docs/api.md) вручную и уже расходится в деталях (202, коды ошибок, 0-based пагинация) | Подключить `springdoc-openapi`, генерировать TS-типы (`openapi-typescript`) — исчезнут расхождения вида `TicketResponse`/`TicketResponseOld` |
| 15 | **Нет `ETag`/`If-Match` и `PATCH`**: конфликты ловятся через `@Version` → `409` ([GlobalExceptionHandler:202-206](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java:202-L206)); частичные обновления — только полной заменой (`PUT /portals/shared/{id}/users` заменяет весь список) | Для оптимистичных блокировок в UI добавить `ETag`/`If-Match` (тогда конфликт виден до отправки) либо возвращать `version` в DTO; для частичных изменений — `PATCH` |
| 16 | **`If-Range` принудительно игнорируется** ([AttachmentController:101-103](src/main/java/com/ukhanov/realhelpdesk/feature/attachmentmanager/controller/AttachmentController.java:101-L103)) — валидаторов нет | Либо отдавать `ETag` (хеш объекта в MinIO уже есть — `TokenHasher`/метаданные), либо оставить и задокументировать: клиент ведёт докачку по своему состоянию |
| 17 | **Ручная сборка `problem+json` в двух местах**: `JwtAuthFilter.unauthorized` пишет JSON строкой ([строки 115-122](src/main/java/com/ukhanov/realhelpdesk/core/filter/JwtAuthFilter.java:115-L122)), `RateLimitInterceptor` собирает `Map` ([строки 64-70](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/interceptor/RateLimitInterceptor.java:64-L70)) — и оба **без экранирования** и без `timestamp`, который добавляет `ProblemResponses` | Переиспользовать `ProblemResponses` + `ObjectMapper`. В `JwtAuthFilter` `detail` подставляется в JSON конкатенацией: сообщение с кавычкой даст битый JSON, и `response.json()` у клиента упадёт |
| 18 | **Дефолтный `AuthenticationEntryPoint` = `Http403ForbiddenEntryPoint`** (Spring Security выбирает его, если entry point не задан, а `anonymous` выключен — [WebSecurityConfiguration:53](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:53)) | Основной путь закрыт `JwtAuthFilter` (401 + `problem+json`), но если запрос дойдёт до `AuthorizationFilter` без аутентификации, ответ будет **403 с пустым телом**, а не 401 и не `problem+json`. Задать свой `AuthenticationEntryPoint` и `AccessDeniedHandler` с единым форматом |
| 19 | **Путаница URL в порталах**: `GET /portals/{id}` — информация, `GET /portals/shared/{id}` — настройки, `GET /portals/shared` (без id) — список доступных порталов | Переименовать в `/portals/{id}/settings` и `/portals/accessible` |
| 20 | **`DELETE /portals?ids=1,2`** — набор в query, ответ 200 + тело `{count, deletedIds}` ([PortalController:125-129](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/controller/PortalController.java:125-L129)) | Работает (тело не нужно, прокси не порежут), но смешивает «удалить ресурс» и «массовая операция». Либо 204 без тела, либо `POST /portals/bulk-delete` |
| 21 | **RPC-стиль поверх REST**: `/owner-transfer/confirm|reject|cancel`, `/notifications/{id}/read` | Допустимо и читаемо для клиента; зафиксировать как осознанное решение (альтернатива — `PATCH` статуса) |
| 22 | **Раздача файлов зависит от `static.base-url`** ([AttachmentResponse:13-14](src/main/java/com/ukhanov/realhelpdesk/feature/attachmentmanager/dto/AttachmentResponse.java:13-L14)) | Если файлы отдаются с другого origin, для них нужен CORS и `jwt.cookie.domain` (иначе 401) — связать это в документации и добавить в prod-preflight (см. [infraReview.md](audit/infraReview.md)) |
| 23 | **Капча — бинарный ответ `image/jpeg`** ([CaptchaController:26-33](src/main/java/com/ukhanov/realhelpdesk/core/security/captcha/controller/CaptchaController.java:26-L33)) | В `<img src>` работает и без CORS (no-cors), но тогда клиент не увидит `429`/`400` — только битую картинку. Для корректной обработки ошибок использовать `fetch` + `URL.createObjectURL` |
| 24 | **`GET /notifications/wait` — исключение из правила «не кэшировать»**: у ответов нет `Cache-Control` | Для long polling и `/unread-count` добавить `Cache-Control: no-store`, чтобы прокси/браузер не отдавали закешированный результат |

---

## 5. Сводная карта эндпоинтов

| Метод | Путь | Тело / параметры | Успех | Замечание для React |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | `RegisterRequest`, `?capId` | 201 + `Location: /users/profile`, пустое тело, 2 `Set-Cookie` | Регистрация сразу логинит; профиль надо запросить отдельно |
| POST | `/api/v1/auth/login` | `{email,password}` | 200, пустое тело, 2 `Set-Cookie` | Тела нет — данные пользователя отдельным запросом |
| POST | `/api/v1/auth/tokens/access` | refresh-cookie | 200 + новая access-cookie | Основной путь тихого refresh |
| GET | `/api/v1/auth/tokens/refresh` | — | 200 `{tokenStatus, createdAt}` | — |
| GET | `/api/v1/auth/session` | — | 200 `{authorization:"Активен"}` | P1: русский текст, нет id пользователя |
| DELETE | `/api/v1/auth/session` | — | 204 + сброс cookie | — |
| GET | `/api/v1/users/profile` | — | 200 `UserInfoResponse` | — |
| PUT | `/api/v1/users/profile` | `UserInfoRequest` | 200 + ресурс | — |
| POST | `/api/v1/users/password-resets` | `{email}` | 202 | P1: код недокументирован |
| PUT | `/api/v1/users/password-resets/{code}` | `{password}` | 204 | Код в пути — маскируется в логах |
| GET | `/api/v1/captcha` | `?capId` (≤10) | 200 `image/jpeg` | P2: использовать `fetch`+blob для обработки ошибок |
| GET | `/api/v1/health` | — | 200 `{status,timestamp}` | Поверхностный (см. [infraReview.md](audit/infraReview.md)) |
| POST | `/api/v1/email/confirmations/{token}` | — | 204 | P0-риск CSRF (POST без тела) |
| GET | `/api/v1/email/info` | — | 200 `EmailInfoResponse` | — |
| DELETE | `/api/v1/email/notifications/{event}` | — | 204 | — |
| POST | `/api/v1/email/codes` | `?capId&capCode` | 202 | Параметры в query |
| POST | `/api/v1/portals` | `CreatePortalRequest` | 201 + `Location` + `{id}` | — |
| GET | `/api/v1/portals` | `page,size,sortBy,order` | 200 `PageResponse<PortalResponse>` | — |
| GET | `/api/v1/portals/shared` | то же | 200 `PageResponse<PortalResponse>` | P2: имя неоднозначно |
| GET | `/api/v1/portals/ids` | — | 200 `[Long]` | P1: массив без обёртки |
| GET | `/api/v1/portals/info` | — | 200 `[PortalInfoResponse]` | P1: массив без обёртки |
| GET | `/api/v1/portals/{id}` | — | 200 `PortalInfoResponse` | — |
| PUT | `/api/v1/portals/{id}` | `UpdatePortalInfoRequest` | 200 + ресурс | — |
| GET | `/api/v1/portals/shared/{id}` | — | 200 `PortalSettingsResponse` | P2: имя |
| PUT | `/api/v1/portals/shared/{id}/users` | `{userIds[]}` | 204 | Полная замена списка |
| GET | `/api/v1/portals/shared/{id}/visibility` | — | 200 `{isPublic}` | — |
| PUT | `/api/v1/portals/shared/{id}/visibility` | `{isPublic}` | 204 | — |
| DELETE | `/api/v1/portals?ids=` | `ids` | 200 `{count, deletedIds}` | P2: query-набор |
| POST | `/api/v1/portals/{id}/owner-transfer` | `PortalTransferRequest` | 201 + `Location` + тело | — |
| GET | `/api/v1/portals/{id}/owner-transfer` | — | 200 | 404, если запроса нет (намеренно) |
| POST | `.../owner-transfer/confirm` | `{password, reason}` | 200 | Пароль в теле — только POST, только HTTPS |
| POST | `.../owner-transfer/reject` | `{reason}` | 200 | — |
| POST | `.../owner-transfer/cancel` | — | 200 | POST без тела |
| GET | `/api/v1/portals/{id}/history` | `page,size` | 200 `PageResponse` | Нет сортировки |
| POST | `/api/v1/portals/{p}/tickets` | `CreateTicketRequest` | 201 + `Location` + `{id}` | — |
| GET | `/api/v1/portals/{p}/tickets` | `status,page,size,sortBy,order` | 200 `PageResponse<TicketResponseOld>` | P1: `Old` в контракте |
| GET | `/api/v1/portals/{p}/tickets/ids` | `status,noAnswer` | 200 `[Long]` | Массив без обёртки |
| GET | `/api/v1/portals/{p}/tickets/{t}` | — | 200 `TicketResponseOld` | — |
| PUT | `.../tickets/{t}/status` | `{status}` | 204 | — |
| PUT | `.../tickets/{t}/priority` | `{priority}` | 204 | — |
| DELETE | `.../tickets/{t}` | — | 204 | — |
| GET | `/api/v1/tickets` | `search,startDate,endDate,status,priority,mine,page,size,sort` | 200 `PageResponse<TicketResponse>` | P1: `size` без лимита |
| GET | `/api/v1/tickets/mine` | `page,size,sortBy,order` | 200 `PageResponse<TicketResponseOld>` | P1: дубль `/tickets?mine` |
| POST | `.../tickets/{t}/messages` | `{messageText}` | 201 + `Location` (**404-маршрут**) | P1: битый `Location` |
| GET | `.../tickets/{t}/messages` | — | 200 `[MessageResponse]` | P1: нет `authorId`, нет `ORDER BY` |
| POST | `.../tickets/{t}/attachments` | `multipart`: `file`, `messageText` | 201 + `Location` = `downloadUrl` | — |
| GET | `.../tickets/{t}/attachments` | — | 200 `[AttachmentResponse]` | — |
| GET | `.../attachments/{a}` | `Range`, `If-Range` | 200 / 206 / 416 | P2: `Content-Disposition`/`Content-Range` не видны из JS |
| GET | `/api/v1/notifications` | `page,size,sortBy,order,unreadOnly` | 200 `PageResponse` | — |
| GET | `/api/v1/notifications/unread-count` | — | 200 `{count}` | Для бейджа |
| GET | `/api/v1/notifications/wait` | `afterId,timeoutSec,size` | 200 страница (возможно пустая) | P2: long polling, 429 на IP |
| PUT | `/api/v1/notifications/{id}/read` | — | 204 | — |
| PUT | `/api/v1/notifications/read-all` | — | 204 | — |
| GET/PUT | `/api/v1/notifications/preferences` | `NotificationPreferencesRequest` | 200 + ресурс | GET возвращает и полный набор событий |

**Ошибки (единый формат):** `application/problem+json` → `{type, title, status, detail, instance, timestamp}` + `errors: {поле: сообщение}` для ошибок валидации ([ProblemResponses:19-44](src/main/java/com/ukhanov/realhelpdesk/core/exception/ProblemResponses.java:19-L44), [GlobalExceptionHandler:107-143](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java:107-L143)). `429` дополнительно несёт `Retry-After`.

---

## 6. Что уже сделано правильно (для React)

| Решение | Почему это ценно клиенту |
|---|---|
| Ошибки по RFC 9457 + `errors` по полям | Форма может подсветить конкретные поля без разбора текста: `errors: { "email": "Некорректный формат Email" }` |
| `PageResponse` вместо сырого `Page` | Нет утечки внутренней структуры Spring, `last` упрощает бесконечный скролл |
| `Instant` в ISO-8601 (UTC) | `new Date(iso)` работает без хаков; никаких `LocalDateTime` без зоны |
| `HttpOnly; Secure` cookie + refresh ограничен путём `/api/v1/auth` | Клиентский код не хранит токены (XSS не украдёт), refresh-cookie не летит на всё API |
| `201 Created` + `Location` + тело `{id}` на создании | Клиент сразу получает id и URL |
| `204 No Content` на командах | Понятная семантика «изменение применено» |
| `Retry-After` и `detail` с секундами на `429` | Можно корректно показать «повторите через N сек» (после правки CORS — и прочитать заголовок) |
| `Range` + потоковая отдача файлов, `Accept-Ranges`, `416` | Докачка больших файлов на слабой сети |
| `downloadUrl` в `AttachmentResponse` | Клиент не собирает URL сам; работает и при выносе файлов на отдельный origin |
| `Cache-Control` на отдаче файлов, `Vary: Origin` от Spring | Кэширование и CDN не путают ответы разных origin'ов |
| Оповещения на `recipientId` без вложенных сущностей | Плоские DTO без lazy-графов — предсказуемый JSON |
| Пустой `Query-string` не логируется, коды в пути маскируются | Отсутствие утечек, о которых клиенту не надо думать |

---

## 7. Готовые рецепты

### 7.1 CORS: закрывает §2.1 и §2.2

```java
// AppConfig#corsConfigurationSource
CorsConfiguration configuration = new CorsConfiguration();
configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
// ключевое: без этого preflight с Content-Type: application/json отклоняется 403
configuration.setAllowedHeaders(List.of("*"));   // Spring эхом вернёт запрошенные заголовки
configuration.setExposedHeaders(List.of(
        "Location",                                  // 201 Created
        "Content-Disposition", "Content-Range", "Accept-Ranges",  // файлы
        "Retry-After",                               // 429
        "X-Access-Token-Expired", "X-Auth-Token-Missing", "X-Malformed-Token",
        "X-Invalid-Token", "X-Verify-Token-Failed"   // маркеры 401 из JwtAuthFilter
));
configuration.setAllowCredentials(true);
configuration.setMaxAge(3600L);
```

### 7.2 Клиентский слой: единая обработка `problem+json` и refresh по маркеру

```ts
export type ProblemDetail = {
  type: string; title: string; status: number; detail: string;
  instance?: string; timestamp?: string; errors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(readonly status: number, readonly problem: ProblemDetail, readonly headers: Headers) {
    super(problem.detail ?? problem.title);
  }
  get fieldErrors() { return this.problem.errors ?? {}; }
  get tokenExpired() { return this.headers.get('X-Access-Token-Expired') === 'true'; }
  get retryAfterSec() { const v = this.headers.get('Retry-After'); return v ? Number(v) : undefined; }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: 'include',                                  // без этого cookie не уйдут
    headers: { ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
  });

  if (res.status === 401 && res.headers.get('X-Access-Token-Expired') === 'true') {
    const refreshed = await fetch('/api/v1/auth/tokens/access', { method: 'POST', credentials: 'include' });
    if (refreshed.ok) return api<T>(path, init);              // один повтор
    window.location.assign('/login');
    throw new ApiError(401, await res.json().catch(() => null));
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({ status: res.status, detail: res.statusText } as ProblemDetail));
    throw new ApiError(res.status, problem, res.headers);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}
```

### 7.3 SSE вместо long polling для оповещений (набросок)

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    waitRegistry.register(currentUserProvider.getCurrentUserId(), () -> {
        try { emitter.send(SseEmitter.event().name("notification").data(/* DTO */ ...)); }
        catch (IOException e) { emitter.completeWithError(e); }
    });
    emitter.onCompletion(() -> waitRegistry.unregister(userId, callback));
    return emitter;
}
```

Клиент: `new EventSource('/api/v1/notifications/stream', { withCredentials: true })` — один канал вместо висящего запроса каждые 25 секунд; снимается и проблема лимита на IP при нескольких вкладках.

---

## 8. Чек-лист

- [ ] **P0** `setAllowedHeaders` (иначе все JSON-мутации с другого origin → 403 на preflight)
- [ ] **P0** `setExposedHeaders`: маркеры 401, `Location`, `Content-Disposition`, `Content-Range`, `Accept-Ranges`, `Retry-After`
- [ ] **P0** Решить вопрос CSRF: `SameSite=Lax` для односайтового фронта либо double-submit токен / обязательный кастомный заголовок
- [ ] **P1** Один DTO заявки вместо `TicketResponse` + `TicketResponseOld`
- [ ] **P1** Добавить `authorId`/`uploadedById` в `MessageResponse`, `AttachmentResponse`, `TicketResponse`
- [ ] **P1** `GET /auth/session` → объект с `authenticated` и данными пользователя (id)
- [ ] **P1** Единый стиль пагинации + `spring.data.web.pageable.max-page-size=100` + задокументировать 0-based
- [ ] **P1** Починить `Location` у `POST .../messages`
- [ ] **P1** Договориться о кодах `PUT` (200+ресурс vs 204) и привести к одному
- [ ] **P1** Обернуть массивы (`{items: []}`) на `/portals/ids`, `/portals/info`, `/tickets/ids`, `/messages`, `/attachments`
- [ ] **P1** Добавить `ORDER BY` в выборку сообщений
- [ ] **P2** Задать свои `AuthenticationEntryPoint`/`AccessDeniedHandler` с `problem+json` вместо дефолтного 403 с пустым телом
- [ ] **P2** Убрать ручную сборку JSON в `JwtAuthFilter` и `RateLimitInterceptor` (переиспользовать `ProblemResponses` + `ObjectMapper`, добавить экранирование)
- [ ] **P2** `springdoc-openapi` + генерация TS-типов
- [ ] **P2** `ETag`/`If-Match` и `PATCH` для частичных обновлений
- [ ] **P2** Рассмотреть SSE/WebSocket для оповещений вместо long polling
- [ ] **P2** `Cache-Control: no-store` на `wait` и `unread-count`
- [ ] **P2** Переименовать `/portals/shared*` в однозначные пути
- [ ] **P3** Задокументировать в [docs/api.md](docs/api.md) коды 202/204/401/403/409/416/429 и `Retry-After`
