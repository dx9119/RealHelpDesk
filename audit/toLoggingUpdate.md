# toLoggingUpdate.md — что не покрыто логированием и что добавить

Аудит логирования по `src/main`: **208 java-файлов, 43 класса с `@Slf4j`, 126 вызовов `logger.<level>(`**.
Документ отвечает на вопрос: **какие участки кода не оставляют следов в логах** — отдельно для профиля `prod` и для профиля `debug`, — и **где обязателен MDC**.

Действующие правила, которые документ не меняет: [docs/code-style.md](docs/code-style.md) (§ «Логирование и комментарии»), уровни — [application-debug.properties](src/main/resources/application-debug.properties), [application-prod.properties](src/main/resources/application-prod.properties), шаблоны — [logback-spring.xml](src/main/resources/logback-spring.xml).

> **Ревизия 2 (после внедрения [apiReview.md](apiReview.md), коммиты `c5e3838`…`f4fbfcd`).** Пересчитаны объёмы
> (было 200/42/120), обновлены ссылки на строки (код сдвинулся), добавлены пункты **A7, A8, B14, C7, C8** —
> они появились вместе с CSRF-фильтром, обработчиками Spring Security, bulk-удалением, SSE-каналом и springdoc.
> Пункты прежних ревизий остаются открытыми: работа по `apiReview.md` логирование не трогала, кроме логов,
> добавленных в PATCH-обновлениях ([PortalManageService:382](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:382),
> [UserManageService:101](src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/service/UserManageService.java:101))
> и в SSE-канале ([NotificationManageService:110-133](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:110-L133)).

> **Ревизия 3 (внедрение плана из §8, шаги 0–6 — выполнены).** Статус пунктов:
>
> - **Таблица A закрыта целиком (A1–A8).** A7 — `loggingFilter` теперь регистрируется первым из трёх ([WebSecurityConfiguration:47-49](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:47-L49)): отказ CSRF получает MDC и попадает в access-лог; A6 — `catch` в `safePortal` сужен до доменного `PortalException` с WARN, сбой БД больше не превращается в `portal=null`.
> - **Таблица B закрыта целиком (B1–B14)**, включая jti-трассировку выдачи токенов (B8: `GenTokenService` пишет `jti`, `SaveTokenService` — id записи в БД).
> - **Таблица C закрыта целиком (C1–C8).** C6 закрыт иначе: `ConstraintValidator` не получает имя поля, поэтому имена отклонённых полей пишет WARN в [GlobalExceptionHandler:117-119](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java:117-L119), а в [NoHtmlValidator](src/main/java/com/ukhanov/realhelpdesk/core/annotation/NoHtmlValidator.java) остался DEBUG без значения; C7 — springdoc в prod приглушён (`logging.level.org.springdoc=ERROR`), спека и UI остаются доступными.
> - **§5 закрыт.** Снимок контекста запроса в long polling и SSE (п. 4, 4b); в `finally` колбэков теперь **возвращается прежний контекст будящего потока**, а не слепой `MDC.clear()` — будить может поток чужого HTTP-запроса, у которого своя строка access-лога; контекст инициатора прокинут в `NotificationPublisher.wakeAfterCommit` и в WARN [NotificationWaitRegistry](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationWaitRegistry.java) (п. 5); технический id у планировщика повторов (п. 6).
> - **§7 закрыт**: пустые MDC-колонки рисуются двумя дефисами (`%X{requestId:---}`), `code-style.md` переформулирован, добавлен `src/test/resources/logback-test.xml`, явный `spring-boot-starter-logging` убран из `pom.xml`, WARN springdoc на старте подавлены в prod.
> - Правила MDC (новый ключ, фон/колбэки, `LogSanitizer.uri`, `requestId` у prod-строк WARN+) внесены в [docs/code-style.md](docs/code-style.md), § «Логирование и комментарии».
>
> Дополнительно: убрана утечка кода восстановления в сообщение `UsernameNotFoundException` ([UserDomainService](src/main/java/com/ukhanov/realhelpdesk/core/security/user/service/UserDomainService.java)) — строка уходила в WARN advice. Критерий готовности §8 выполнен; открытых пунктов из таблиц A/B/C и §5/§7 не осталось.

---

## 0. Как читать таблицы

| Обозначение | Значение |
|---|---|
| `prod` | Уровень, при котором строка **видна в prod**: `root=INFO`, `com.ukhanov=INFO` → видны только `INFO`/`WARN`/`ERROR`. `—` = в prod эту строку логировать не нужно (шум) |
| `debug` | Уровень для профиля `debug` (`com.ukhanov=DEBUG`). `—` = не логировать даже в debug |
| `MDC-ключи` | Какие ключи обязаны быть в строке. В prod в шаблоне есть только `requestId`; `clientIp`/`uriRequest` видны в debug |
| Правило | MDC-ключи брать **из констант** `LoggingFilter.REQUEST_ID` / `CLIENT_IP` / `URI_REQUEST`, а не строками — иначе паттерн и код разъедутся |

Правило выбора уровня (действует во всех таблицах ниже):

- `ERROR` — сбой, требующий вмешательства, обязательно со стеком;
- `WARN` — отказ или аномалия, которую надо видеть в prod (клиент получил не то, данные повреждены, операция не выполнена);
- `INFO` — завершённое бизнес-событие (видно в prod);
- `DEBUG` — трассировка и подробности (видно только в debug).

**Категорически нельзя логировать**: значения access/refresh-токенов и `rawToken`, пароли, коды восстановления и подтверждения, содержимое cookie и `Authorization`, query-string, тела писем. Путь запроса — только через `LogSanitizer.uri(...)`. Сущности JPA в `toString()` не подставлять (lazy-связи) — логировать `id`.

Наблюдение из живого стека: пустые MDC-колонки рендерятся **одним** дефисом (`| - | - | - |`), а не двумя — см. §7 п. 1.

---

## 1. Что сделано правильно (не трогать и не «улучшать»)

| # | Участок | Почему это верно |
|---|---|---|
| 1 | [WebSecurityConfiguration:47-49](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:47-L49) | `loggingFilter` вставлен **перед** `JwtAuthFilter` → MDC стоит раньше JWT-логов. Это контракт: `loggingFilter` обязан оставаться перед `JwtAuthFilter`. **Но** с появлением CSRF-фильтра порядок перестал быть достаточным — см. A7 (CSRF стоит перед `loggingFilter`) |
| 2 | [LoggingFilter:50-52](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:50-L52), [:66](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:66) | MDC ставится до `doFilter`, чистится в `finally` → нет утечки между запросами на переиспользуемых потоках |
| 3 | [LoggingFilter:37-40](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:37-L40), [:88-98](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:88-L98) | ASYNC-dispatch (long polling и SSE) логируется, `requestId` восстанавливается из атрибута запроса → id тот же, что у исходного |
| 4 | [LoggingFilter:60](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:60), [:75-79](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:75-L79) | исключение в цепочке → ERROR, 5xx → ERROR, 4xx → INFO, 2xx → DEBUG: access-лог покрывает все контроллеры |
| 5 | [GlobalExceptionHandler:49-236](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java:49-L236) | Граница исключений MVC: бизнес-отказы (`ApiException`/`ApiRuntimeException`) → WARN, аутентификация/JWT/валидация → WARN, необработанное → ERROR со стеком |
| 6 | [LogSanitizer:11](src/main/java/com/ukhanov/realhelpdesk/core/log/LogSanitizer.java:11) | Код сброса пароля и токен подтверждения в пути маскируются; query-string не логируется вообще |
| 7 | [RateLimitInterceptor:56](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/interceptor/RateLimitInterceptor.java:56) | IP для лимита берётся тем же `ClientIpResolver`, что и MDC `clientIp` → лог и счётчик согласованы |
| 8 | [CsrfProtectionFilter:60](src/main/java/com/ukhanov/realhelpdesk/core/filter/CsrfProtectionFilter.java:60) | Сам отказ CSRF логируется (WARN) с путём через `LogSanitizer.uri` — логировать есть что, вопрос только в MDC и access-логе (A7) |

---

## 2. Таблица A — «немые» catch-блоки и отказы вне MVC

Найдено 6 таких мест в прежней ревизии; в текущей — **8**: A7 и A8 пришли с CSRF-фильтром и обработчиками Spring Security.

| # | Pri | Участок | Что не покрыто | prod | debug | MDC-ключи | Что логировать |
|---|---|---|---|---|---|---|---|
| A1 | P3 | [RangeHeader:111](src/main/java/com/ukhanov/realhelpdesk/core/http/RangeHeader.java:111) | `NumberFormatException` → `-1`, заголовок `Range` молча игнорируется, клиент получает файл целиком | `—` | `DEBUG` | `requestId`, `uriRequest` | `logger.debug("Некорректный Range, отдан полный файл, uri={}", LogSanitizer.uri(uri))` |
| A2 | P2 | [AttachmentController:141-143](src/main/java/com/ukhanov/realhelpdesk/feature/attachmentmanager/controller/AttachmentController.java:141-L143) | `InvalidMediaTypeException` → подмена на `application/octet-stream`; признак битых метаданных вложения | `WARN` | `DEBUG` | `requestId`, `uriRequest` | `logger.warn("Некорректный content-type вложения «{}», отдаю octet-stream", contentType)` |
| A3 | P3 | [PortalManageService:212](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:212) | `UsernameNotFoundException` → заглушка «Пользователь не существует» в списке участников портала | `—` | `DEBUG` | `requestId`, `uriRequest` | `logger.debug("Участник портала {} не найден в БД, подставлена заглушка", userId)` |
| A4 | P3 | [PortalManageService:247](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:247) | То же для истории портала (`resolveUserName`) | `—` | `DEBUG` | `requestId`, `uriRequest` | `logger.debug("Автор записи истории {} не найден, имя заменено", userId)` |
| A5 | P3 | [PortalOwnerTransferService:414](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:414) | `UsernameNotFoundException` → `null`, в ответе о передаче имя станет «Пользователь не существует» | `—` | `DEBUG` | `requestId`, `uriRequest` | `logger.debug("Участник передачи {} удалён, имя не подставлено", userId)` |
| A6 | **P1** | [PortalOwnerTransferService:419-425](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:419-L425) | `catch (Exception e) → null`: ловится **любое** исключение, включая сбой БД, и молча превращается в «портала нет». Ответ уйдёт с `portal=null`, в логах — ни следа | `WARN` | `WARN` (со стеком) | `requestId`, `uriRequest` | `logger.warn("Не удалось прочитать портал {} для ответа о передаче: {}", portalId, e.getMessage(), e)` + сузить `catch` до доменного исключения |
| A7 | **P2** | [CsrfProtectionFilter:60-63](src/main/java/com/ukhanov/realhelpdesk/core/filter/CsrfProtectionFilter.java:60-L63) | Отказ CSRF (403) — единственный WARN, **невидимый в access-логе** и без MDC: `loggingFilter` зарегистрирован в цепочке *после* CSRF ([WebSecurityConfiguration:47-49](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:47-L49)), поэтому не оборачивает отклонённый запрос. Проверено на живом стеке: `WARN \| - \| - \| - \| CsrfProtectionFilter \| Отклонён запрос без CSRF-заголовка…`, строки `LoggingFilter \| HTTP POST … -> 403` нет вовсе | `WARN` | `WARN` | `requestId`, `clientIp`, `uriRequest` | Переставить регистрацию: `addFilterBefore(loggingFilter, JwtAuthFilter.class)` **до** строки с CSRF — тогда `loggingFilter` станет самым внешним, MDC появится, а access-лог закроет отказ. Альтернатива — заполнять MDC внутри самого фильтра |
| A8 | P3 | [ProblemJsonAuthenticationEntryPoint](src/main/java/com/ukhanov/realhelpdesk/core/security/handler/ProblemJsonAuthenticationEntryPoint.java), [ProblemJsonAccessDeniedHandler](src/main/java/com/ukhanov/realhelpdesk/core/security/handler/ProblemJsonAccessDeniedHandler.java) | 401/403, которые Spring Security отдаёт в обход MVC (fallback, если запрос не перехватил `JwtAuthFilter`), не оставляют ни строки: исключение до `GlobalExceptionHandler` не доходит — там обрабатывается только `AuthorizationDeniedException` от `@PreAuthorize` ([GlobalExceptionHandler:67-69](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java:67-L69)) | `WARN` | `WARN` | `requestId`, `uriRequest` | `logger.warn("Запрос без аутентификации: {} {}", method, LogSanitizer.uri(uri))` и `logger.warn("Доступ запрещён: {} {}", method, LogSanitizer.uri(uri))` |

---

## 3. Таблица B — бизнес-события, которые нигде не записаны

| # | Pri | Участок | Что не покрыто | prod | debug | MDC-ключи | Что логировать |
|---|---|---|---|---|---|---|---|
| B1 | **P1** | [LoginService:31-52](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/login/service/LoginService.java:31-L52) → [GetTokenService:48-51](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/GetTokenService.java:48-L51) | **Успешный вход** логируется только если отозвано `revoked > 0` ([GetTokenService:49-50](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/GetTokenService.java:49-L50)). Первый вход после регистрации и вход сразу после логаута (активных refresh нет) не оставляют **ни одной строки**. Отказы при этом есть ([:33](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/login/service/LoginService.java:33), [:40](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/login/service/LoginService.java:40), [:45](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/login/service/LoginService.java:45)) — и пишут `email` в WARN, то есть PII в prod, тогда как B13 предлагает держать адрес только в DEBUG | `INFO` | `INFO` | `requestId` | `logger.info("Вход выполнен, userId={}", user.getId())`; в отказах заменить `email` на `userId` либо осознанно принять адрес в WARN как средство разбора атак |
| B2 | P2 | [EmailPolicyService:46-70](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailPolicyService.java:46-L70) + [EmailPolicyController:48-52](src/main/java/com/ukhanov/realhelpdesk/core/mail/controller/EmailPolicyController.java:48-L52) | Пользователь включил/снял «тишину» по email-событиям — при разборе «почему не пришло письмо» это первый вопрос, ответа в логах нет | `INFO` | `INFO` | `requestId`, `uriRequest` | `logger.info("Пользователь {} изменил email-оповещения: событие={}", userId, level)` (для снятия — `"…вернул оповещения"`) |
| B3 | P2 | [EmailDeliveryService:125-132](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailDeliveryService.java:125-L132) | Подтверждение email (`confirmEmail`) — нет записи об успехе; саму попытку с неверным кодом логирует advice (WARN) | `INFO` | `INFO` | `requestId`, `uriRequest` | `logger.info("Email подтверждён, userId={}", user.getId())` — **без значения токена** |
| B4 | P2 | [EmailDeliveryService:134-140](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailDeliveryService.java:134-L140) + [:72](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailDeliveryService.java:72) | Запрос кода подтверждения отправляется как `NEW_SYSTEM_MESSAGE`, поэтому INFO-строка «Письмо отправлено, event=NEW_SYSTEM_MESSAGE» не отличает код подтверждения от системного сообщения | `INFO` | `INFO` | `requestId`, `uriRequest` | Либо отдельное значение `NotificationEvent`, либо `logger.info("Отправлен код подтверждения email, userId={}", user.getId())` |
| B5 | P2 | [EmailDeliveryService:115-123](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailDeliveryService.java:115-L123) | Рассылка участникам портала: при `EmailAccessDeniedException` на одном получателе цикл обрывается, остальные писем не получат — и в логе не видно, на каком `userId` оборвалось | `WARN` | `WARN` | `requestId`, `uriRequest` | `logger.warn("Рассылка прервана на получателе {}: {}", userId, e.getMessage())` + решить, должен ли цикл продолжаться |
| B6 | **P1** | [NotificationRepeatService:49](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationRepeatService.java:49), [:73](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationRepeatService.java:73) | Планировщик повторов (раз в 60 с) пишет **только DEBUG** → в prod фоновая механика напоминаний полностью невидима: не понять, работает она или нет | `INFO` (только если `woken` не пуст) | `DEBUG` | см. §5 п. 6 | `logger.info("Созданы повторные оповещения: получателей={}", woken.size())` |
| B7 | P2 | [EmailLogService:23-33](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailLogService.java:23-L33) | Журнал отправок и расчёт квоты: при 429 «исчерпан лимит восстановления» не видно, сколько писем уже учтено в окне | `—` | `DEBUG` | `requestId` | `logger.debug("Писем по событию {} за окно {}: {} (лимит {})", event, window, count, limit)` |
| B8 | P3 | [GenTokenService:31-69](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/GenTokenService.java:31-L69), [SaveTokenService](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/SaveTokenService.java), [DecodeTokenService](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/DecodeTokenService.java) | Выдача и сохранение токенов не оставляют следа (и это правильно — значения логировать нельзя), но нет и безопасной трассировки по `jti` | `—` | `DEBUG` | `requestId` | `logger.debug("Выдан {} токен, userId={}, jti={}", type, userId, jti)` — только `jti`, никогда сам токен |
| B9 | P3 | [RefreshService:35-50](src/main/java/com/ukhanov/realhelpdesk/core/security/auth/refresh/service/RefreshService.java:35-L50) | Успешное обновление access-токена не логируется; отказ — логируется (advice WARN) | `—` | `DEBUG` | `requestId` | `logger.debug("Access-токен обновлён, userId={}", userId)` |
| B10 | P3 | [LimitService:16-37](src/main/java/com/ukhanov/realhelpdesk/core/security/limiter/service/LimitService.java:16-L37) | Сам отказ по лимиту порталов/участников уходит исключением → WARN в advice (покрыто), но цифры не видны: нельзя понять, что пользователь у границы | `—` | `DEBUG` | `requestId` | `logger.debug("Лимит порталов: текущее={}, лимит={}, userId={}", current, limit, userId)` |
| B11 | P3 | [RateLimitService:23-40](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/service/RateLimitService.java:23-L40) | Решение лимитера (allowed, сколько накоплено, сколько ждать) не логируется; превышение пишет только `RateLimitInterceptor` (WARN) | `—` | `DEBUG` | `clientIp`, `uriRequest` | `logger.debug("Лимит {}: запросов={}, макс={}, allowed={}, retryAfter={}с", key, count, max, allowed, retryAfter)` |
| B12 | P3 | [UserDomainService:22-68](src/main/java/com/ukhanov/realhelpdesk/core/security/user/service/UserDomainService.java:22-L68), [CustomUserDetailsService:18-26](src/main/java/com/ukhanov/realhelpdesk/core/security/user/service/CustomUserDetailsService.java:18-L26) | «Пользователь не найден» / «неизвестный код восстановления» — источник `UsernameNotFoundException`; наружу обрабатывается (WARN), внутренней трассировки нет | `—` | `DEBUG` | `requestId` | `logger.debug("Пользователь не найден по {}, идентификатор={}", kind, identifier)` |
| B13 | P3 | [EmailDeliveryService:45-77](src/main/java/com/ukhanov/realhelpdesk/core/mail/service/EmailDeliveryService.java:45-L77) | INFO-строка «Письмо отправлено, event=…» не содержит ни `userId`, ни получателя → нельзя подтвердить, ушло ли письмо конкретному пользователю (адрес намеренно только в DEBUG) | `INFO` (дополнить) | `INFO` | `requestId` | `logger.info("Письмо отправлено, event={}, userId={}", sourceEvent, userId)` — адрес оставить в DEBUG, чтобы не тащить PII в prod |
| B14 | P3 | [PortalManageService:257-288](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:257-L288) | Массовое удаление (`POST /portals/bulk-delete`) не пишет итог: не видно, сколько порталов запрошено и сколько удалено (`DeleteResult` на [:287](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:287) нигде не логируется). Сбои по отдельным порталам покрыты WARN на [:283](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:283) | `INFO` | `INFO` | `requestId`, `uriRequest` | `logger.info("Массовое удаление порталов: запрошено={}, удалено={}, userId={}", requested.size(), deletedIds.size(), user.getId())` |

---

## 4. Таблица C — служебная диагностика (не события, но без неё нельзя разобрать инцидент)

| # | Pri | Участок | Что не покрыто | prod | debug | MDC-ключи | Что логировать |
|---|---|---|---|---|---|---|---|
| C1 | P2 | [ClientIpResolver:35-48](src/main/java/com/ukhanov/realhelpdesk/core/http/ClientIpResolver.java:35-L48) | Не видно, откуда взят IP (заголовок или сокет) и когда сработал фолбэк. Без этого нельзя объяснить расхождение «IP в логе ≠ IP в лимите» | `—` | `DEBUG` | `requestId` | `logger.debug("IP клиента: источник={}, trustProxyHeaders={}", source, trust)` |
| C2 | P3 | [PaginationAdapter:32-44](src/main/java/com/ukhanov/realhelpdesk/core/pagination/service/PaginationAdapter.java:32-L44) | `@Slf4j` есть, вызовов нет; отказ по `sortBy` уходит исключением → WARN в advice (покрыто). После унификации пагинации через `PaginationAdapter` идут **все** списки, включая `GET /tickets` и историю портала | `—` | `DEBUG` | `requestId` | `logger.debug("Пагинация: page={}, size={}, sort={}", page, size, sortBy)` |
| C3 | P3 | [CurrentUserProvider:23-34](src/main/java/com/ukhanov/realhelpdesk/core/security/user/CurrentUserProvider.java:23-L34) | `@Slf4j` есть, вызовов нет. При отсутствии аутентификации летит `NullPointerException` → в prod это ERROR «Перехвачено необработанное исключение» со стеком, хотя ситуация штатная для защиты | `—` | `DEBUG` | `requestId` | `logger.debug("Текущий пользователь: id={}", userId)`; NPE заменить на доменное исключение |
| C4 | P3 | [MessageDomainService](src/main/java/com/ukhanov/realhelpdesk/domain/message/service/MessageDomainService.java) | `@Slf4j` есть, вызовов нет. Выборка сообщений теперь с обязательным `ORDER BY id` — при разборе порядка ленты это единственная точка | `—` | `DEBUG` | `requestId` | Либо трассировка выборки (`ticketId`, `size`, `firstId/lastId`), либо снять `@Slf4j` (дублирует INFO из `MessageManageService`) |
| C5 | P3 | [RateLimitService:46-51](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/service/RateLimitService.java:46-L51) | Лимитер хранит окна в памяти; при аномальном числе ключей (`> SWEEP_THRESHOLD`) не будет ни одной строки — утечку не заметить | `WARN` (при пороге) | `DEBUG` | `—` (фон) | `logger.warn("Окон рейт-лимита больше порога: {}", windows.size())` |
| C6 | P3 | [NoHtmlValidator](src/main/java/com/ukhanov/realhelpdesk/core/annotation/NoHtmlValidator.java) | Отклонённая разметка видна только как WARN «Ошибка валидации тела запроса» без указания поля. Новые PATCH-DTO ([PatchPortalInfoRequest](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/dto/PatchPortalInfoRequest.java), [PatchUserInfoRequest](src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/dto/PatchUserInfoRequest.java)) используют тот же `@NoHtml` | `—` | `DEBUG` | `requestId` | `logger.debug("Отклонена HTML-разметка в поле {}", field)` |
| C7 | P3 | springdoc: [pom.xml:82-88](pom.xml:82-L88), [WhiteUrlConfig:15-18](src/main/java/com/ukhanov/realhelpdesk/core/config/WhiteUrlConfig.java:15-L18) | `/v3/api-docs` и `/swagger-ui/**` открыты без токена и попадают в access-лог (в debug — поток запросов за статикой UI), а сам springdoc на **каждом** старте пишет два WARN `SpringDoc /v3/api-docs endpoint is enabled by default…` — в prod они видны | `INFO` (access) / `WARN` (старт) | `DEBUG` | `requestId` | Решить осознанно: в prod выключить (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) либо поднять уровень `org.springdoc=ERROR` в [application-prod.properties](src/main/resources/application-prod.properties); при желании — исключить UI-пути из access-лога |
| C8 | P3 | [NotificationController:61-67](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/controller/NotificationController.java:61-L67) → [LoggingFilter:60-79](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:60-L79) | SSE-канал живёт до 30 минут: строка access-лога появляется только при закрытии соединения, поэтому «висит ли канал» по логам не видно — только `SSE-канал открыт/закрыт` в debug ([NotificationManageService:132](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:132), [:261-262](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:261-L262)) | `INFO` при закрытии | `DEBUG` | `requestId` | `logger.info("SSE-канал закрыт: клиент отключился или истёк таймаут, userId={}", userId)` — либо признать исключением и задокументировать |

> Все 7 классов с `@Slf4j` без единого вызова (`EmailPolicyService`, `PaginationAdapter`, `DecodeTokenService`, `GenTokenService`, `SaveTokenService`, `CurrentUserProvider`, `MessageDomainService`) разобраны в таблицах B и C — «мёртвых» аннотаций после внедрения не остаётся. Набор классов не изменился: новые файлы (`CsrfProtectionFilter`, `ProblemResponseWriter`, `EntityTag`, обработчики Spring Security) либо уже логируют, либо не должны (см. §6).

---

## 5. MDC: где нужен и где сейчас теряется

MDC — thread-local: в новый поток он сам не переносится. В проекте четыре места, где контекста запроса уже нет, и один приём, где это решено правильно.

| # | Участок | Состояние | Что делать |
|---|---|---|---|
| 1 | [LoggingFilter:50-52](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:50-L52) | ✅ ставит `requestId`, `clientIp`, `uriRequest`; чистит в `finally` | оставить |
| 2 | [WebSecurityConfiguration:47-49](src/main/java/com/ukhanov/realhelpdesk/core/config/WebSecurityConfiguration.java:47-L49) | ⚠️ `loggingFilter` перед `JwtAuthFilter` — верно, но **после** `csrfProtectionFilter` → JWT-логи ([JwtAuthFilter:77-112](src/main/java/com/ukhanov/realhelpdesk/core/filter/JwtAuthFilter.java:77-L112)) идут с `requestId`, а отказ CSRF — нет (A7) | зарегистрировать `loggingFilter` **первым** из трёх (`addFilterBefore` до строки с CSRF) |
| 3 | [LoggingFilter:37-40](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:37-L40), [:88-98](src/main/java/com/ukhanov/realhelpdesk/core/filter/LoggingFilter.java:88-L98) | ✅ ASYNC-dispatch long polling и SSE логируется повторно, `requestId` берётся из атрибута запроса — тот же, что у исходного | оставить как эталон |
| 4 | [NotificationManageService:81-89](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:81-L89) | ⚠️ колбэк `onWake` long polling исполняется **не** на потоке запроса (его вызывает `wake`), MDC внутри пуст | перенести снимок контекста в колбэк (пример 1 ниже) |
| 4b | [NotificationManageService:110-133](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:110-L133) + [NotificationStream:228-262](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationManageService.java:228-L262) | ⚠️ **новое**: подписчик SSE `run()`/`push()` вызывается из `wake` на потоке публикатора/планировщика — MDC пуст, а логи там уже есть (`SSE-канал … закрыт`). Тот же дефект, что и в п. 4, но во втором месте | тот же снимок контекста; чистить в `finally` — подписка живёт долго и поток возвращается в пул |
| 5 | [NotificationWaitRegistry:57](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationWaitRegistry.java:57) | ⚠️ WARN «Ошибка пробуждения…» пишется на потоке вызывающего (публикатор/планировщик) → без `requestId` | принять как есть либо прокинуть контекст инициатора |
| 6 | [NotificationRepeatService:49](src/main/java/com/ukhanov/realhelpdesk/feature/notificationmanager/service/NotificationRepeatService.java:49) + `NotificationPublisher.wakeAfterCommit` | ⚠️ фон/после коммита: своего `requestId` нет, колонки пустые (`-`) | задать технический идентификатор задачи (пример 2 ниже) |
| 7 | [RateLimitInterceptor:56](src/main/java/com/ukhanov/realhelpdesk/core/security/ratelimit/interceptor/RateLimitInterceptor.java:56) | ✅ `ip=` берётся тем же `ClientIpResolver`, что и MDC `clientIp` | оставить |
| 8 | Правило на будущее | — | при появлении `@Async`/своего `ExecutorService` оборачивать задачи `MDC.getCopyOfContextMap()` → `MDC.setContextMap(...)` и **обязательно** `MDC.clear()` в `finally`: поток возвращается в пул |

**Пример 1 — перенос контекста запроса в поток пробуждения (long polling и SSE — оба колбэка):**

```java
// снимок делается на потоке запроса, до регистрации колбэка
Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();

Runnable onWake = () -> {
    if (mdcSnapshot != null) {
        MDC.setContextMap(mdcSnapshot);
    }
    try {
        // логи здесь уже несут requestId/clientIp/uriRequest исходного запроса
        if (deferred.isSetOrExpired()) {
            return;
        }
        PageResponse<NotificationResponse> fresh = fetchAfter(userId, afterId, size);
        if (!fresh.content().isEmpty()) {
            deferred.setResult(ResponseEntity.ok(fresh));
        }
    } finally {
        MDC.clear(); // поток чужой — контекст не должен утечь
    }
};
```

Для SSE снимок передаётся в `NotificationStream` при создании и восстанавливается в `run()`/`push()` тем же способом.

**Пример 2 — фоновая задача без запроса (технический идентификатор вместо пустых колонок):**

```java
@Scheduled(fixedDelay = SCAN_PERIOD_MS, initialDelay = SCAN_PERIOD_MS)
@Transactional
public void repeatDueNotifications() {
    // ключи — из констант LoggingFilter, чтобы не разъехаться с паттерном
    MDC.put(LoggingFilter.REQUEST_ID, "scheduler-repeat-notifications");
    MDC.put(LoggingFilter.URI_REQUEST, "scheduled:repeat-notifications");
    try {
        // ... тело задачи; clientIp намеренно не заполняем — в колонке останется "-"
    } finally {
        MDC.clear();
    }
}
```

Правила по MDC, которые стоит внести в [docs/code-style.md](docs/code-style.md):

1. Новый MDC-ключ = правка **двух** паттернов в [logback-spring.xml](src/main/resources/logback-spring.xml) + константа в `LoggingFilter`.
2. Логи в фоновых задачах и в колбэках `DeferredResult`/`SseEmitter` обязаны либо восстановить контекст, либо явно задать технический `requestId`.
3. Путь в `uriRequest` — всегда через `LogSanitizer.uri(...)`.
4. Любая строка уровня WARN и выше, видимая в prod, обязана нести `requestId` — иначе инцидент не связать с запросом (сейчас нарушено в A7 и §5 п. 5, 6).

---

## 6. Что логировать не нужно (уже закрыто)

| Участок | Почему не надо |
|---|---|
| 12 контроллеров (`AttachmentController`, `AuthController`, `TicketController`, `PortalController`, `NotificationController`, `MessageController`, `UserController`, `TicketSearchController`, `PortalTransferController`, `CaptchaController`, `EmailPolicyController`, `HealthCheckController`) | Строку запроса и итоговый статус пишет `LoggingFilter` (2xx → DEBUG, 4xx → INFO, 5xx → ERROR). Дублировать «метод вызван» запрещено правилом [code-style.md:113](docs/code-style.md:113). Новые эндпоинты (`GET .../messages/{id}`, `POST /portals/bulk-delete`, `PATCH /portals/{id}`, `PATCH /users/profile`, `GET /notifications/stream`) попадают в тот же механизм |
| Обновления ресурсов, которые уже логируются | `PUT/PATCH` портала → INFO ([PortalManageService:333](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:333), [:382](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:382)); профиль → INFO ([UserManageService:71](src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/service/UserManageService.java:71), [:101](src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/service/UserManageService.java:101)); передача владения → INFO ([PortalOwnerTransferService:110](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:110), [:157](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:157), [:181](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:181), [:203](src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalOwnerTransferService.java:203)) |
| 12 репозиториев, 7 мапперов, 9 `*Properties`, 7 конфигов, `EntityTag`, `ItemsResponse`, `SessionResponse`, PATCH-DTO | Логировать нечего; ошибки БД поднимаются исключением и попадают в advice. `EntityTag.satisfiedBy` — чистая функция (412 отдаёт сервис, WARN пишет advice) |
| `HealthCheckController` | Обязан оставаться тихим, иначе пробы забьют лог |
| Отказы бизнес-логики **на уровне MVC** | Любой `ApiException`/`ApiRuntimeException` (включая новый `412 PRECONDITION_FAILED` от `If-Match`), `UsernameNotFoundException`, `BadCredentialsException`, `JwtException`, `AuthorizationDeniedException` от `@PreAuthorize`, ошибки валидации — уже WARN в [GlobalExceptionHandler](src/main/java/com/ukhanov/realhelpdesk/core/exception/GlobalExceptionHandler.java). Отдельные логи в подсистемах не нужны. **Исключение** — отказы вне MVC: CSRF (A7) и `AuthorizationFilter` (A8) |
| `StorageBucketInitializer`, `MinioStorageService`, `EmailDeliveryService.sendEmail` | Старт хранилища (INFO/ERROR) и отправка письма (INFO) уже покрыты |
| `ProblemResponseWriter` | Пишет ответ, а не событие: логировать сам факт формирования problem+json нечего — это делает тот, кто отказ обнаружил (фильтр, интерсептор, advice) |

---

## 7. Мелкие правки рядом (не про покрытие, но про корректность логов)

1. **`%X{requestId:--}` отрисует один дефис, а не два.** В Logback разделитель значения по умолчанию — `:-`, поэтому в `requestId:--` он съедает первые два символа. Подтверждено логами живого стека: пустые колонки выглядят как `| - | - | - |`. Если нужен именно `--`, писать `%X{requestId:---}` (аналогично `clientIp`, `uriRequest`) в [logback-spring.xml:6](src/main/resources/logback-spring.xml:6) и [:9](src/main/resources/logback-spring.xml:9). Косметика, но колонки будут выглядеть как задумано.
2. **[code-style.md:106](docs/code-style.md:106)** предписывает явный `private static final Logger logger = LoggerFactory.getLogger(X.class);`, что противоречит [строке 78](docs/code-style.md:78) («логгер из `@Slf4j`») и подталкивает к коллизии имени `logger` (Lombok тогда не генерирует поле, а лишь выдаёт предупреждение). Переформулировать как «`@Slf4j` разворачивается в …».
3. **Нет `logback-test.xml`.** `logback-spring.xml` подхватывает только Spring Boot, поэтому в чистых unit-тестах Logback уходит на дефолт (root DEBUG в консоль). Если нужен предсказуемый вывод тестов — добавить `src/test/resources/logback-test.xml` с тем же паттерном.
4. **`spring-boot-starter-logging` в [pom.xml:97-101](pom.xml:97-L101)** объявлен явно, хотя приходит транзитивно — безвредный дубль.
5. **springdoc добавил собственную зависимость** ([pom.xml:82-88](pom.xml:82-L88)) и пишет WARN при старте — учесть в prod-профиле (см. C7), иначе первый же рестарт даёт две строки уровня WARN ни о чём.

---

## 8. Порядок внедрения

| Шаг | Что | Пункты | Зачем в первую очередь |
|---|---|---|---|
| 0 | Порядок фильтров: MDC и access-лог для отказов CSRF | A7 | Одна перестановка строки в `WebSecurityConfiguration` возвращает и MDC, и строку access-лога для 403 — сейчас единственный видимый в prod отказ полностью «слепой» |
| 1 | Аудит безопасности: вход, email-настройки, планировщик | B1, B2, B6 | Без них в prod не видно успешных входов, смены email-политики и работы фоновых напоминаний |
| 2 | Скрытые сбои | A6, A2, B5, A8 | Молчаливая подмена исключения на `null`, обрыв рассылки и «немые» 401/403 — реальные потери, которые сейчас не диагностируются |
| 3 | MDC в фоне, long polling и SSE | §5 п. 2, 4, 4b, 5, 6 | Иначе новые логи из шага 1 в фоновых ветках и подписке SSE придут без `requestId` |
| 4 | Бизнес-события и диагностика | B3, B4, B7, B13, B14, C1 | Разбор обращений «письмо не пришло / IP не тот / почему удалилось не всё» |
| 5 | Трассировка debug | A1, A3, A4, A5, B8–B12, C2–C6 | Наполняет профиль `debug`, в prod не шумит |
| 6 | Шум и мелкие правки | C7, C8, §7 | springdoc и SSE — новые источники шума/слепых зон; остальное косметика и документация |

**Критерий готовности:** для каждого пункта из таблиц A, B, C есть либо строка лога с нужным уровнем и MDC, либо осознанное решение «не логируем» с обоснованием; в проекте не остаётся классов с `@Slf4j` без вызовов (сейчас 7 из 43); все строки из фоновых потоков несут либо `requestId` запроса-инициатора, либо технический идентификатор задачи; **любая строка уровня WARN и выше, видимая в prod, несёт `requestId`, а отклонённые запросы попадают в access-лог** (A7 — единственное известное нарушение).
