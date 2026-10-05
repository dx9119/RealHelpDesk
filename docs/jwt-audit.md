# Аудит JWT-аутентификации (RealHelpDesk)

**Ветка:** `audit/jwt-security` (база — `prod`)
**Дата:** 2026-09-29
**Область:** генерация/хранение/валидация JWT, cookie, логаут, смена пароля, refresh-поток, логи, рейт-лимиты.

## Что было сделано

1. Полное чтение JWT-цепочки: `JwtConfig`, `WebSecurityConfiguration`, `WhiteUrlConfig`, `JwtAuthFilter`, `GenTokenService`, `ValidTokenService`, `DecodeTokenService`, `FindTokenService`, `GetTokenService`, `ChangeTokenService`, `RefreshService`, `LoginService`, `LogoutService`, `RegistrationService`, `AuthController`, `UserManageService`, `SecurityUser`, `CurrentUserProvider`, `RefreshTokenModel`, репозитории, `application.properties`, `.env(.example)`, `docker-compose.yaml`, `pom.xml`, `logback-spring.xml`.
2. Исправление критичных и высоких находок (см. таблицу), включая две проблемы, найденные при e2e-прогоне (№ 10, 11).
3. Прогон `mvn test` — 157 тестов, 0 ошибок.
4. E2E-скрипт `scripts/e2e-jwt-flow.sh` — 21/21 проверок на живом стеке.

---

## Исправлено

| # | Нахождение | Где было | Исправление |
|---|---|---|---|
| 1 | **Нет отзыва access-токенов.** Фильтр не обращался к БД: после логаута, смены пароля или блокировки JWT оставался рабочим до 60 минут | `JwtAuthFilter` | Добавлен claim `ver` в access-JWT и поле `UserModel.tokenVersion`. `JwtAuthFilter` → новый `AccessTokenAuthService` загружает пользователя из БД и сверяет версию/статус. Инкремент версии — в `LogoutService.processLogout` и `UserManageService.setNewPasswd` |
| 2 | **Смена пароля не отзывала старые refresh-токены (инверсия бага).** `setNewPasswd` сначала создавала новый токен, затем `getActiveRefreshToken` возвращал **только что созданный** и помечала `PASSWD_CHANGE` **его**; старые активные оставались валидными | `UserManageService.java:116-125` | Все активные refresh-токены помечаются `PASSWD_CHANGE`; метод стал `@Transactional` |
| 3 | **Статус пользователя не проверялся.** `isEnabled/isAccountNonLocked` нигде не вызывались — заблокированный пользователь продолжал работать | `SecurityUser`, `LoginService`, `JwtAuthFilter` | Проверка `UserStatus.ACTIVE` при логине и в фильтре на каждый запрос |
| 5 | **Токены в логах/ответах.** Значения cookie писались в INFO/DEBUG, а значение refresh-токена попадало в текст `TokenException` и уходило клиенту в JSON | `FindTokenService:34,38,39`, `GetTokenService:99`, `DecodeTokenService:67`, `ChangeTokenService:41`, `TokenExceptionHandler`, `LoggingFilter` | Значения токенов из логов убраны (логируются id/статус); `TokenExceptionHandler` больше не отдаёт `cause` и тексты ошибок jjwt; `LoggingFilter` не пишет query-string (там код сброса пароля) |
| 6 | **Refresh-токен в БД открытым текстом**, без индекса/уникальности | `RefreshTokenModel`, `FindTokenService` | В БД хранится SHA-256 хеш (`TokenHasher`, 64 симв., `unique`); сырой токен — только transient-поле для cookie. Поиск строго по хешу; старые строки (сырой токен) переводятся в хеш одним SQL из `scripts/sql/2026-09-29-jwt-security-audit.sql` — runtime-совместимости со старым форматом нет. Побочно: **login теперь ротирует refresh** (сырой токен из БД не восстановить) |
| 7 | **Rate limit отсутствовал на `POST /api/v1/auth/update`** — единственном критичном auth-эндпоинте без лимита | `AuthController` | `@RateLimit(requests = 30, windowSeconds = 300)` |
| 8 | **Тип токена не разграничен** — refresh-cookie, подставленная вместо access, аутентифицировала пользователя с ролью `ROLE_null` | `GenTokenService`, `ValidTokenService`, `JwtAuthFilter` | Claim `typ` (`access`/`refresh`); `ValidTokenService.lowLevelVerifyToken(token, expectedType)` проверяет тип. Плюс: `getNewAccessToken` теперь **всегда** верифицирует токен (раньше — только при `ACTIVE`); `RefreshService` ловит `JwtException` (истёкший refresh раньше давал 409 с текстом jjwt и не помечался `REVOKED`) |
| 4 (част.) | **Cookie refresh-токена на `path=/`** — уходил на все эндпоинты API | `AuthController` | `path=/api/v1/auth`; `SameSite` вынесен в свойство `jwt.cookie.same-site` (дефолт `None` — поведение не меняется) |
| 10 | **JWT без `jti`** — два токена одного пользователя, выданные в одну секунду, байт-в-байт идентичны → при уникальном индексе на хеше `jwt_tokens.token_refresh` логин падал 500 (`DuplicateKeyException`) | `GenTokenService` | Обоим токенам добавлен `.id(UUID.randomUUID().toString())` (jti) |
| 11 | **Пустая `accessToken`-cookie → необработанная ошибка.** jjwt бросает `IllegalArgumentException: CharSequence cannot be null or empty`, который не ловится фильтром → 500/403 вместо 401 | `JwtAuthFilter.resolveToken`, `DecodeTokenService` | Пустые значения cookie считаются отсутствующими; `decodeJwtClaims` нормализует пустой токен в `MalformedJwtException` → 401 |

### Ответ на вопрос о CSRF

CSRF **не зависит от наличия серверных сессий**: он защищает от того, что браузер сам приклеивает cookie к любому cross-site запросу. Связка «`csrf(disable)` + `SameSite=None`» — реальная поверхность для CSRF (браузер отправит cookie жертвы на cross-site POST/DELETE). Смягчено сужением `path` refresh-cookie. Полноценные варианты:

- если фронт на том же сайте/поддомене, что и API — переключить `JWT_COOKIE_SAMESITE=Lax` (и `csrf` можно оставить выключенным);
- если фронт кросс-доменный — оставить `None` и либо включить CSRF-токен, либо требовать кастомный заголовок (например `X-Requested-With`) в state-changing запросах: браузер не может отправить его с чужого origin без preflight, а CORS-политика его не разрешает.

---

## Осталось (зафиксировано, не чинилось)

| # | Нахождение | Уровень |
|---|---|---|
| 9 | **Нет тестов на JWT** — ни фильтр, ни генерация, ни валидация, ни refresh-поток не покрыты (по договорённости — вне объёма) | Средне |
| — | Ротация refresh **не выполняется при каждом `/auth/update`** (токен живёт 30 дней, украденный refresh валиден до отзыва/срока) | Средне |
| — | Таблица `jwt_tokens` растёт: при каждом входе создаётся строка, истёкшие не переводятся в `REVOKED` (нужна периодическая очистка) | Низко |
| — | Один DB-запрос на запрос к API в `AccessTokenAuthService` (+ существующие в `CurrentUserProvider`) — при росте нагрузки нужен кэш/Redis | Низко |
| — | `SecurityUser.getAuthorities()` возвращает роль без `ROLE_`, фильтр — с двойным префиксом было `ROLE_ROLE_*`; сейчас authority формируется из БД, но `hasRole` нигде не используется (все проверки — через `@PreAuthorize` с сервисами) | Низко |
| — | Роль зашита в JWT claim `role` (используется только для диагностики; авторизация берёт роль из БД) | Низко |
| — | Мёртвый код: `WhiteUrlConfig.isMyTelegramBot` (сравнение заголовка с пустой строкой), `LogoutResponse` | Низко |
| — | `pom.xml`: `spring-boot-starter-oauth2-resource-server` подключён, но не настроен | Низко |
| — | `docker-compose`: `ddl-auto=create-drop` (рестарт = потеря данных/сессий), заглушки issuer/audience — в ветке `feat/liquibase-migrations` схему ведёт Liquibase, `ddl-auto=validate` | Средне |
| — | Дефолты креденшелов в `application.properties` (`demo-keystore-pass`, `pass`, `password`), капча выключена по умолчанию | Средне |
| — | `POST /api/v1/auth/check`, `/token`, `DELETE /cookies` — без rate limit | Низко |
| — | Ранний выход в `LoginService` до BCrypt — потенциальный timing-оракул user enumeration; коды 409 вместо 401 в auth-ошибках | Средне |
| — | CORS `allowedOrigins` — плейсхолдеры (`https://localhost.ru`, `https://example.com`) | Средне |

## Что сделано правильно (без изменений)

- Токены в http-only + secure cookie, не возвращаются в теле ответа; `TokensResponse.toString()` маскирует токены.
- Подпись через `verifyWith`/`parseSignedClaims` (jjwt 0.13) — `alg=none` и key-confusion отсекаются; проверяются `iss`, `aud`, `exp`, `sub`.
- Секрет — fail-fast (`JwtConfig`), в `.env` 256 бит для HS256, `.env` не в git.
- `SessionCreationPolicy.STATELESS`, `anonymous(disable)`, whitelist совпадает с контроллерами.
- Refresh реально отзывается по статусу в БД; rate limit есть на login/register/passwd-reset/captcha; `trust-proxy-headers=false` по умолчанию.

---

## Миграция и ломающие изменения

1. **Схема БД** (только для сред с `ddl-auto=none`): `scripts/sql/2026-09-29-jwt-security-audit.sql`
   - `users.token_version` (новая колонка);
   - хеширование существующих `jwt_tokens.token_refresh` + unique-индекс.
   - В docker-compose (`create-drop`) скрипт не нужен.
   - С веткой `feat/liquibase-migrations` структуру ведёт Liquibase
     (`src/main/resources/db/changelog`), а этот скрипт вошёл в baseline
     `001-baseline-schema`: новые среды получают схему целиком, существующие
     изменения помечаются выполненным — выполнять скрипт вручную больше не нужно.
2. **Все выданные access-токены перестают действовать** (нет claim `typ`/`ver`) — пользователи войдут заново. Старые refresh-токены валидны (при успешной миграции строк), новые выдаются при входе.
3. Новая настройка: `jwt.cookie.same-site` / `JWT_COOKIE_SAMESITE` (по умолчанию `None` — без изменения поведения).
4. Refresh-cookie теперь отправляется только на `/api/v1/auth/*` — фронтону, читающему его с других путей, нужно обновиться (эндпоинты API токен из этой cookie не читают).

## Проверка

- `mvn test` — **157 tests, 0 failures, 0 errors** (`BUILD SUCCESS`).
- Обновлён `UserManageServiceTest.setNewPasswd_shouldUpdatePasswordAndInvalidateOldTokens` — теперь проверяет отзыв **всех** активных refresh-токенов и инкремент версии access-токенов.
- **E2E-скрипт `scripts/e2e-jwt-flow.sh`** (нужен запущенный `docker compose up -d`) — **21/21 OK**:
  - refresh-cookie как access → 401;
  - `/auth/update` выдаёт новый access, старый работает;
  - логаут → старый access 401, refresh 409;
  - смена пароля → все токены отозваны (401/409), вход с новым паролем 200;
  - отказы: неверный пароль 409, без cookie 401.
- Состояние БД после e2e подтверждает исправления 1/2/6: `token_version` инкрементирован, все токены хранятся как хеш длиной 64, после смены пароля у пользователя нет ни одного `ACTIVE` refresh (кроме выданного новым входом).
- Повторный запуск скрипта может упереться в rate limit `passwd-reset/request` (3/10 мин) — скрипт об этом сообщает; сброс счётчиков: `docker compose restart app`.
