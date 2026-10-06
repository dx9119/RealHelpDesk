# RealHelpDesk API

**RealHelpDesk API** — это REST API для построения системы управления заявками или внутренней системы обработки запросов.

Реализован MVP (Minimum Viable Product):
- Регистрация, аутентификация, авторизация, верификация email пользователя, восстановление пароля.
- Создание порталов - точки в рамках которых формируется тематика заявок.
- Создание заявок в рамках портала.
- Создание сообщений в рамках заявки.
- Создатель портала может предоставить доступ к нему другим пользователям.
- Email-оповещения.
- Выбор уровня ограничения оповещений.
- In-app оповещения о новых заявках в доступных порталах: long polling по HTTP (`/api/v1/notifications`), прочтение, настройки событий и повторов на пользователя.
- Реализован поиск заявки (в рамках указанного временного промежутка, содержимому заявки, ФИО автора).
- Проверка лимитов пользователя (разрешенное кол-во порталов, кол-во общих пользователей порталов).
- Лимиты на попытки восстановления доступа.
- Капча.

## Авторизация и безопасность

Используются JWT-токены + HTTP cookies для аутентификации и авторизации (токены передаются через куки) пользователей. Каждый входящий запрос проходит через фильтр `JwtAuthFilter`, который:
- Пропускает открытые эндпоинты (например, `/auth/login`) из белого списка.
- Извлекает access-токен из cookies
- Расшифровывает и проверяет access-токен с помощью `ValidTokenService` (подпись, срок, issuer/audience, тип `typ=access`)
- Проверяет пользователя в БД: статус аккаунта и версию токенов (`ver`) — логаут/смена пароля отзывают выданные access-токены немедленно.
- Устанавливает аутентификацию в `SecurityContextHolder` (роль берётся из БД).
- Помечает ошибки токена (истёкший токен, некорректный формат и т.д.) через доп. заголовки.

Refresh-токен в БД хранится в виде SHA-256 хеша; выдаётся клиенту в http-only cookie с `path=/api/v1/auth`. При каждом входе и регистрации выдаётся свежий refresh, а все ранее активные refresh-токены пользователя переводятся в `REVOKED` — старый перестаёт работать сразу. Настройка `jwt.cookie.same-site` (`JWT_COOKIE_SAMESITE` в `docker/app.env`) — см. `docs/jwt-audit.md`.

## Структура проекта

- Core - конфигурация (spring security/CORS etc.), глобальные исключения, фильтры, токены, пользователи, почтовые оповещения, пагинация, проверка лимитов, контроль доступа в рамках контроллера.
- Domain - базовые entity, репозитории и сервисы.
- Feature - управление порталами, заявками, сообщениями, пользователями, оповещениями.

## Конфигурация

Конфигурация разложена по смыслу: значения — в трёх файлах,
`application.properties` — схема без дефолтов, домены — в
`application-domains.properties`.

| Файл | Что в нём |
|---|---|
| `src/main/resources/application.properties` | Схема: обязательные `${VAR}` без дефолтов. Единственное значение — `spring.jpa.hibernate.ddl-auto=validate`, закреплённое в коде (окружением не управляется) |
| `src/main/resources/application-domains.properties` | Схема доменов (подключается через `spring.config.import`): CORS, issuer/audience токенов, домен фронта в письмах, адреса отправителя |
| `docker/app.env` | Продукт: рейт-лимиты, капча, сроки токенов, брендинг, адреса писем |
| `docker-compose.yaml` → `services.app.environment` | Окружение: порты, хосты, профиль, ресурсы, JVM — и секреты через `${VAR:?}` (пустой `MAIL_PASSWORD` допустим) |
| `.env` (см. `.env.example`) | Только секреты: `JWT_SECRET`, `DB_PASSWORD`, `MAIL_PASSWORD`, `KEY_STORE_PASS`, `SHARDINGSPHERE_ROOT_PASSWORD`, `SHARDINGSPHERE_SHARDING_PASSWORD` |

Отсутствующая переменная валит приложение на старте с
`Could not resolve placeholder '<VAR>'`, отсутствующий или пустой
обязательный секрет (`JWT_SECRET`, `DB_PASSWORD`, `KEY_STORE_PASS`) —
`docker compose` ещё до запуска. `MAIL_PASSWORD` пустым быть может:
smtp4dev работает без аутентификации. Пароли прокси шардирования
(`SHARDINGSPHERE_*_PASSWORD`) обязательны только при включённом профиле
`sharding`: compose поднимается и с пустыми значениями, но прокси без
пароля не стартует (`shardingsphere/entrypoint.sh`). Проект рассчитан на работу
**только внутри Docker-контейнера**: без переменных окружения локальный
`java -jar` не поднимется.

Единственный дефолт в коде — `SameSite.NONE` у `jwt.cookie.same-site`
(поле `JwtProperties.Cookie`). Допустимые значения — весь набор enum `SameSite`:
`None`, `Lax`, `Strict` (регистр не важен); неизвестное значение валит старт.
Настройка в `application.properties` обязательна (`${JWT_COOKIE_SAMESITE}`).

Схему и оба места со значениями (`services.app.environment` + `docker/app.env`)
держит в согласии тест `ApplicationConfigSchemaTest`: значения вида `${VAR}`
без литералов, списки переменных совпадают один в один, а один ключ не может
быть задан в обоих файлах. Детали сборки, контекста и секретов — в
[`sec.md`](sec.md).

## Запуск проекта
1. `cp .env.example .env` и заполните секреты: `JWT_SECRET` (`openssl rand -base64 32`),
   `DB_PASSWORD` и `KEY_STORE_PASS` — без них `docker compose` не стартует.
   `MAIL_PASSWORD` может остаться пустым (smtp4dev без аутентификации);
   в `.env.example` для `KEY_STORE_PASS` задано демо-значение. Пароли
   прокси шардирования (`SHARDINGSPHERE_*_PASSWORD`) заполняются, только
   если будете включать профиль `sharding`.
2. `docker compose up --build -d`

> **Схема БД.** Структуру создаёт и изменяет Liquibase при старте приложения,
> Hibernate только сверяет entity с ней (`spring.jpa.hibernate.ddl-auto=validate`,
> значение закреплено в `application.properties`). Рестарт
> контейнера `app` данные не трогает; `docker compose down -v` удаляет том
> PostgreSQL вместе с ними.

Всё остальное правится в `docker/app.env` (капча, рейт-лимиты, сроки,
адреса писем), в `docker-compose.yaml` → `services.app.environment`
(порты, почта, профиль) или в `.env` — пересборка образа не нужна,
достаточно `docker compose up -d`.
PostgreSQL (`5432`) и SMTP (`25`) слушают только `127.0.0.1`; API — `8443`,
интерфейс smtp4dev — `3000`. Шарды (`5433`, `5434`) и прокси шардирования
(`3307`) поднимаются только с включённым профилем `sharding` (см. раздел
ниже) и без него не слушают ничего.

### Шардирование (ShardingSphere)

**По умолчанию выключено** — режим неактивности: сервисы `shardingsphere`,
`postgres_shard_0` и `postgres_shard_1` объявлены в профиле `sharding`
Docker Compose и обычным `docker compose up -d` не поднимаются — активна
одна БД (`postgres`). (Это профиль compose, а не Spring-профиль `debug`/`prod`.)
Конфигурация шардирования при этом уже готова и лежит в репозитории:
шаблоны прокси, правила раскладки и схема шардов.

Включение (тома с данными шардов, если были, сохраняются):

```bash
docker compose --profile sharding up -d
# либо COMPOSE_PROFILES=sharding в .env — тогда обычный `up -d` поднимет всё
```

Выключение (обычный `up -d` уже запущенные шарды не останавливает —
профильные сервисы вне модели без профиля, поэтому остановка явная):

```bash
docker compose stop shardingsphere postgres_shard_0 postgres_shard_1
```

Пароли прокси (`SHARDINGSPHERE_ROOT_PASSWORD`,
`SHARDINGSPHERE_SHARDING_PASSWORD` в `.env`) нужны только при включённом
профиле: compose без профиля поднимается и с пустыми значениями, а прокси
без них отказывается стартовать (`shardingsphere/entrypoint.sh`) — секретов
по умолчанию в git по-прежнему нет.

Помимо основной БД compose умеет поднять два шарда и прокси между ними:

| Сервис | Образ | Порты | Что это |
|---|---|---|---|
| `postgres_shard_0` | `postgres:18` | `127.0.0.1:5433` | первый шард |
| `postgres_shard_1` | `postgres:18` | `127.0.0.1:5434` | второй шард |
| `shardingsphere` | `apache/shardingsphere-proxy:5.5.3` | `127.0.0.1:3307` | ShardingSphere-Proxy: единая точка входа к шардам |

Шарды при первой инициализации тома получают схему приложения — тем же
файлом `db/changelog/sql/001-baseline-schema.sql`, который применяет
Liquibase на основной базе (копии в репозитории нет, единственный источник
правды один). Правила раскладки строк — `shardingsphere/conf/database-sharding.yaml`:
значение ключа mod 2 по двум шардам, для таблиц со своим первичным ключом
ключ указан явно.

**По умолчанию приложение ходит в основной `postgres`** — трафик `app`
шардирование не затрагивает. Подключение к прокси (при запущенном профиле)
— одна переменная в `.env`, без правки файлов и пересборки:

```bash
DB_URL=jdbc:postgresql://shardingsphere:3307/desk
docker compose up -d        # app переподключится к прокси
```

Перед переключением в серьёз (всё перечислено и в комментариях конфигурации
прокси): выбрать ключ шардирования с co-location связанных строк, заменить
генерацию `id` из последовательностей на генератор прокси и снять межшардовые
внешние ключи.

Проверка вручную (профиль `sharding` запущен, пароли в `.env`):

```bash
PGPASSWORD="$SHARDINGSPHERE_ROOT_PASSWORD" psql \
  -h 127.0.0.1 -p 3307 -U root -d desk -c 'select count(*) from users;'
```

Конфигурация прокси — шаблоны `shardingsphere/conf/*.yaml` с плейсхолдерами
`__NAME__`: `shardingsphere/entrypoint.sh` подставляет в них значения из
окружения контейнера при каждом старте, поэтому паролей в git нет.

### Обновление PostgreSQL 15 → 18

PostgreSQL 18 держит данные в каталоге со своим мажорным версионом
(`PGDATA=/var/lib/postgresql/18/docker`), поэтому том `postgres_data`
теперь монтируется на `/var/lib/postgresql` — родительский каталог, а не
`/var/lib/postgresql/data`, как в 15–17. Том со старыми данными образ 18
не поднимет: контейнер завершится с ошибкой «upgrade the underlying
database using pg_upgrade».

Перенос данных (дамп + чистая инициализация):

```bash
# на ветке с postgres:15 — дамп, затем остановка
docker compose exec postgres pg_dump -U user -d desk > desk-pg15.sql
docker compose down

# новая ветка: удалить только после успешного дампа (путь тома зависит от
# имени проекта compose — здесь realhelpdesk_postgres_data)
docker volume rm realhelpdesk_postgres_data
docker compose up -d postgres
docker compose exec -T postgres psql -U user -d desk < desk-pg15.sql
```

Альтернатива — `pg_upgrade` (том с данными 15-й версии остаётся на месте,
нужен запущенный контейнер со старым образом). На пустом томе (первый
запуск) ничего переносить не нужно.

### Миграции схемы (Liquibase)

Миграции лежат в `src/main/resources/db/changelog`:

| Файл | Что в нём |
|---|---|
| `db.changelog-master.xml` | Корень: подключает changeset'ы по порядку |
| `changes/NNN-<slug>.xml` | Один шаг: `<changeSet id="NNN-<slug>">` и условие его выполнения |
| `sql/NNN-<slug>.sql` | SQL шага, подключается через `<sqlFile path="db/changelog/sql/NNN-<slug>.sql">` |

Старт: `DB_MIGRATIONS_ENABLED=true` и `DB_MIGRATIONS_CHANGE_LOG=classpath:db/changelog/db.changelog-master.xml`
в `docker-compose.yaml` → `services.app.environment` (схема этих ключей — в
`application.properties`). Liquibase выполняется до Hibernate; после него
`spring.jpa.hibernate.ddl-auto=validate` валит запуск, если entity не совпадает
со схемой. Режим задан в коде и переменной окружения не управляется: вернуть
изменивший схему режим помешает `ApplicationConfigSchemaTest` — он падает, если
в конфигурации среды появится что-то кроме `validate`.

Новая миграция:

1. `sql/NNN-<slug>.sql` — SQL (DDL) со следующим свободным номером;
2. `changes/NNN-<slug>.xml` — `<changeSet id="NNN-<slug>" author="...">` c `<sqlFile>`;
3. `<include>` нового файла в `db.changelog-master.xml`.

Applied changeset'ы не редактируются: меняется контрольная сумма и Liquibase
остановит запуск — правка только новым changeset'ом. CHECK-ограничения колонок
с enum зафиксированы в baseline: новое значение enum требует отдельного changeset
с пересозданием ограничения. Baseline (`001-baseline-schema`) на базе, где таблицы
уже есть, помечается выполненным и ничего не меняет — схема новых сред создаётся
полностью, существующие не трогаются.

### HTTPS и keystore

Самоподписанный `keystore.p12` создаётся **при старте контейнера**
(`docker/entrypoint.sh`), а не при сборке: пароль не попадает в слои образа
и в `docker history`, смена `KEY_STORE_PASS` требует только
`docker compose up -d`, а сборка образа от секрета не зависит.
Файлы `*.p12` в репозиторий не попадают.

Для продакшна смонтируйте свой файл в `/app/ssl/keystore.p12` (volume) —
генерация в этом случае пропускается. `KEY_STORE_PASS` обязателен:
Tomcat открывает PKCS12 этим паролем.

## Профили

Два профиля: `debug` и `prod`. Переменная `SPRING_PROFILES_ACTIVE` обязательна:
в `application.properties` нет значения по умолчанию. Локальный
`docker compose` задаёт `debug` в `services.app.environment`.

| Профиль | Логи |
|---|---|
| `debug` | код приложения на `DEBUG`, SQL Hibernate без параметров, в строке request-id и IP |
| `prod` | `INFO` и выше по своему коду, без SQL и без пошаговой трассировки. Успешный HTTP-запрос не пишется |

```yaml
# docker-compose.yaml, services.app.environment
SPRING_PROFILES_ACTIVE: prod
```

В `prod` остаются бизнес-события, отказы входа, превышение лимитов и ошибки. Тело запроса, код восстановления пароля, значение токена и query-string в лог не попадают.

## Регистрация

По умолчанию капча выключена (`CAPTCHA_ENABLED=false` в `docker/app.env`) —
тогда поля `capCode` и параметр `capId` не нужны: отправляйте
`POST /api/v1/auth/register` сразу с телом из `firstName`, `lastName`,
`email` и `password`.

Если капча включена:

1. В Postman отправляем запрос на получение капчи (capId — ID посетителя, генерируем руками или на фронте):
https://localhost:8443/api/v1/captcha?capId=abc123xyz9
2. Смотрим картинку с кодом капчи, указываем его в теле (поле capCode) в запросе на регистрацию:
https://localhost:8443/api/v1/auth/register?capId=abc123xyz9
```
{
  "firstName": "Иван",
  "lastName": "Иванов",
  "email": "test@test.com",
  "password": "SuperStrongPass123",
  "capCode": "356dd"
}
```
3. После регистрации сервер отвечает `201` и ставит cookies `accessToken` и `refreshToken`
   (тела ответа нет) — Postman запомнит их сам.

## Рейт-лимиты

Счетчик идет по IP и эндпоинту и работает одинаково при включенной и выключенной капче. При превышении — ответ `429` с заголовком `Retry-After`.

| Эндпоинт | Ключ (`ratelimit.limits.<key>`) | Лимит |
|---|---|---|
| `GET /api/v1/captcha` | `captcha` | 30 за 60 сек |
| `POST /api/v1/auth/register` | `auth-register` | 10 за 5 мин |
| `POST /api/v1/auth/login` | `auth-login` | 10 за 5 мин |
| `POST /api/v1/auth/tokens/access` | `auth-access-token` | 30 за 5 мин |
| `POST /api/v1/users/password-resets` | `password-reset-request` | 3 за 10 мин |
| `PUT /api/v1/users/password-resets/{code}` | `password-reset-confirm` | 5 за 10 мин |
| `POST /api/v1/email/codes` | `email-code` | 3 за 10 мин |
| `GET /api/v1/notifications/wait` | `notifications-wait` | 30 за 60 сек |
| письма восстановления пароля (лимит на адрес) | `email-recovery` | 3 за 24 часа |

Пороги заданы в `docker/app.env`: `RATE_LIMIT_<KEY>_REQUESTS` — число запросов, `RATE_LIMIT_<KEY>_WINDOW_SECONDS` — окно в секундах. `.env` содержит только секреты; продукт — в `docker/app.env`, окружение — в `docker-compose.yaml` (sec.md §1).

Новый лимит — аннотация `@RateLimit(key = "...")` на методе контроллера плюс два порога в `docker/app.env` под тем же ключом, переведённым в верхний регистр через подчёркивание (`auth-register` → `RATE_LIMIT_AUTH_REGISTER_REQUESTS` и `RATE_LIMIT_AUTH_REGISTER_WINDOW_SECONDS`). Без порогов в конфиге запрос упадает с 500: текст
`Rate limit не задан в конфиге: ratelimit.limits.<key>` уходит в лог
приложения, тело ответа — generic «Операция завершилась неудачей».
Счетчики хранятся в памяти приложения и сбрасываются при рестарте.

За прокси (nginx и т.п.) задайте `TRUST_PROXY_HEADERS: "true"` в `docker-compose.yaml`, иначе все пользователи будут считаться одним IP самого прокси. Без прокси держите `false` — иначе заголовок `X-Forwarded-For` можно подделать и обойти лимит.

## Оповещения

In-app оповещения (`/api/v1/notifications`, только под авторизацией) приходят по HTTP без WebSocket: клиент держит long polling `GET /wait`, сервер отвечает сразу при наличии оповещений новее курсора `afterId` или возвращает пустую страницу по таймауту (`timeoutSec`, 25 по умолчанию, максимум 30).

| Метод и путь | Назначение |
|---|---|
| `GET /api/v1/notifications?page=&size=&unreadOnly=` | список оповещений, новые сверху (`PageResponse`) |
| `GET /api/v1/notifications/unread-count` | счётчик непрочитанных — для значка |
| `GET /api/v1/notifications/wait?afterId=&timeoutSec=&size=` | long polling: страница оповещений новее `afterId`, без новых — ожидание до таймаута (лимит `notifications-wait`) |
| `PUT /api/v1/notifications/{id}/read` | отметить прочитанным (204, чужое — 404); гасит всю группу повторов строки |
| `PUT /api/v1/notifications/read-all` | отметить все прочитанными (204) |
| `GET /api/v1/notifications/preferences` | включённые события и настройки повтора; без сохранённой строки — все события, повтор включён на 30 минут |
| `PUT /api/v1/notifications/preferences` | набор событий + повтор: `{"events": [...], "repeatEnabled": true, "repeatIntervalMinutes": 30}`; поля повтора `null` — не менять, интервал 1..10080 минут |

Кому: владелец портала + доверенные (`allowedUserIds`) — тот же круг, что у email-оповещений, но автор действия не получает уведомления о собственном действии. Каталог in-app событий (`NotificationPublisher.SUPPORTED_EVENTS`): `NEW_TICKET`, `NEW_MESSAGE`, `CHANGE_TICKET`, `TICKET_DELETED`, `NEW_PORTAL`, `PORTAL_DELETED`, `NEW_SYSTEM_MESSAGE`; выбор пользователя хранится в `user_notification_preferences`. Email-мьют (`/api/v1/email/notifications/{event}`) от этих настроек не зависит.

Записи ложатся в таблицу `notifications`; ожидающий `GET /wait` будится после коммита новой строки, поэтому оповещение видно сразу, без задержки до таймаута.

**Повторы.** `NEW_TICKET` и `NEW_MESSAGE` повторяются, пока не прочитаны: каждые `repeatIntervalMinutes` (по умолчанию 30, минимум 1) получателю создаётся новая строка-напоминание с той же группой (`group_id` первоисточника), поэтому она приходит и через long polling, и в списке. Обход — раз в минуту (`NotificationRepeatService`), фактическая периодичность: интервал плюс до минуты обхода. Прочтение любой строки группы (`PUT /{id}/read`) погашает все напоминания, `read-all` — тоже. Выключенный `repeatEnabled` или выключенное событие останавливает повторы; у пользователя без строки настроек повтор включён на 30 минут.

## Тесты и проверки

Тесты гоняются **на хосте, без Docker**: они поднимают собственную
конфигурацию из `src/test/resources/application.properties`, которая
перекрывает продовую схему в classpath.

| Команда | Что делает |
|---|---|
| `mvn test` | юнит-тесты и срезы (`@DataJpaTest` на встроенной H2), 327, включая парность-тест `ApplicationConfigSchemaTest` |
| `mvn verify` | то же + интеграционные `*IT` через failsafe (24): письма через GreenMail, репозитории на H2 |
| `mvn spotless:apply` | форматирование; `spotless:check` и `checkstyle` (`config/checkstyle/checkstyle.xml`) висят на фазе `validate`, поэтому выполняются при любом `mvn test`/`mvn verify` |
| `postman_collection.json` | коллекция для импорта в Postman |

Запуск e2e-сценариев по живому стеку — в разделе ниже.

## Скрипты e2e-проверок

Скрипты гоняют HTTP-сценарии по живому стеку и возвращают ненулевой код
(`echo $?`), если хотя бы одна проверка не сошлась.

| Скрипт | Назначение |
|---|---|
| `scripts/e2e-portal-access.sh` | **Права на портал.** Регистрирует владельца и гостя, владелец публикует портал и выдает гостю доступ. Проверяет: посторонний получает 403 на чтении участников и переименовании (даже когда портал публичный), публично читается только имя/описание, после выдачи доступа доверенный читает и переименовывает (200), `isPublic` меняет только владелец (гость — 403). |
| `scripts/e2e-ratelimit.sh` | **Рейт-лимиты.** Проверяет троттлинг: после 30 успешных `GET /captcha` идет 429 с `Retry-After`, 11-я регистрация и 11-я попытка входа — 429, 4-й запрос сброса пароля — 429, при этом `health` без аннотации остается 200. |
| `scripts/e2e-jwt-flow.sh` | **JWT-поток.** Полный цикл аутентификации: refresh-cookie не работает как access (401), `/auth/tokens/access` выдает новый access, логаут мгновенно отзывает access и refresh (401), смена пароля (через письмо smtp4dev) отзывает все токены, вход с новым паролем работает, отказы — 401. |
| `scripts/e2e-notifications.sh` | **Оповещения.** Long polling: доверенный получает `NEW_TICKET`, пока владелец создаёт заявку; автор действия не получает уведомления о своём действии; смена статуса уведомляет владельца; событие, отключённое в настройках, не приходит (пустой `wait` отвечает 200 с `content=[]` по таймауту); повторы: дефолт 30 минут у пользователя без настроек, напоминание о непрочитанной заявке приходит с интервалом 1 минута и гасится чтением одной строки группы, выключенный повтор напоминаний не даёт (ждёт по ~2.5 минуты); чужое уведомление — 404, неизвестное событие настроек — 400, без токена — 401, `timeoutSec=99` — 400. |

Запуск (нужен поднятый стек `docker compose up -d`, `docker` для запросов
к postgres и выключенная капча — по умолчанию; `e2e-jwt-flow.sh` дополнительно
читает код сброса пароля из smtp4dev на `localhost:3000`):

```bash
bash scripts/e2e-portal-access.sh
bash scripts/e2e-ratelimit.sh
bash scripts/e2e-jwt-flow.sh
bash scripts/e2e-notifications.sh
```

`e2e-ratelimit.sh` исчерпывает лимиты на 5–10 минут: при повторном запуске
до истечения окна он попросит перезапустить приложение
(`docker compose restart app`). `e2e-jwt-flow.sh` после нескольких запусков
упирается в лимит `users/password-resets` (3 / 10 мин) и также порекомендует
перезапуск. Все скрипты создают тестовых пользователей в БД.

