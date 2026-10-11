# infraReview.md — инфраструктура, профили и запуск: аудит и план улучшений

Разбор `infrastructure/docker-compose.yaml`, `Dockerfile`, `scripts/docker/*`, `application*.properties` и `infrastructure/shardingsphere/*`: как проект поднимается в режиме отладки и в рабочем контуре, что этому соответствует инженерной норме для Spring Boot + Spring Data + Docker, и что стоит поправить.

Проверялись только файлы — стек не запускался. Приоритеты: **P0** — блокирует эксплуатацию, **P1** — риск инцидента в проде, **P2** — гигиена, **P3** — улучшение.

**Статус (ревизия 2, 11.10.2026):** все пункты чек-листа §10 внедрены (отложено только #15 secrets/configtree — по решению заказчика); детали и отклонения — §11.

| Раздел | Пунктов | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|
| Критичное (§2) | 7 | 2 | 5 | — | — |
| Docker / Compose (§3) | 10 | — | — | 8 | 2 |
| Конфигурация Spring (§4) | 7 | — | 1 | 4 | 2 |
| Шардирование (§5) | 4 | — | 1 | 3 | — |

---

## 1. Как проект поднимается сейчас

### 1.1 Профиль `debug` (значение по умолчанию)

| Шаг | Что происходит | Где |
|---|---|---|
| 1 | `cp infrastructure/.env.example infrastructure/.env`, заполняются секреты | [.env.example](infrastructure/.env.example) |
| 2 | `make up` = `docker compose -f infrastructure/docker-compose.yaml up --build -d` | [Makefile:64-65](Makefile:64-L65) |
| 3 | Сборка образа: `maven:3.9.6-eclipse-temurin-21` собирает jar (`-DskipTests`), укладывает его слоями, рантайм — `eclipse-temurin:21-jre` | [Dockerfile:3-57](Dockerfile:3-L57) |
| 4 | `scripts/docker/stack.env` подаётся как `env_file` **всем** сервисам; секреты из `infrastructure/.env` инжектятся точечно через `${VAR:?}` в `services.app.environment` | [compose:20-28](infrastructure/docker-compose.yaml:20-L28) |
| 5 | `app` ждёт `postgres` и `minio` по `condition: service_healthy`; для `smtp` — только `service_started` | [compose:29-35](infrastructure/docker-compose.yaml:29-L35) |
| 6 | Entrypoint: keystore создаётся в tmpfs `/app/ssl`, пароль идёт в `keytool` через `:env`, затем `exec java` с флагами JVM | [entrypoint.sh:12-58](scripts/docker/entrypoint.sh:12-L58) |
| 7 | Spring резолвит все `${VAR}` (без дефолтов), поднимает Liquibase, Hibernate в `validate`, включается профиль `debug`: DEBUG по приложению + SQL | [application.properties](src/main/resources/application.properties), [application-debug.properties](src/main/resources/application-debug.properties) |
| 8 | Шардирование остаётся выключенным: сервисы лежат в compose-профиле `sharding` | [compose:108-196](infrastructure/docker-compose.yaml:108-L196) |

### 1.2 Рабочий контур: отдельного пути в репозитории нет

«Prod» сегодня = тот же самый compose, в котором вручную поправлены значения и смонтирован настоящий keystore:

| Что нужно поменять руками | Где лежит сегодня | Кто это проверяет |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | [stack.env:108](scripts/docker/stack.env:108) (закоммичено `debug`) | никто |
| `MAIL_HOST`/`MAIL_PORT` с `smtp` на реальный SMTP | [stack.env:168-170](scripts/docker/stack.env:168-L170) | никто |
| `JWT_COOKIE_SAMESITE`, `CORS_ALLOWED_ORIGINS`, `TRUST_PROXY_HEADERS`, `STATIC_BASE_URL`, домены | [stack.env:57-94](scripts/docker/stack.env:57-L94), [:159-162](scripts/docker/stack.env:159-L162) | никто |
| `CAPTCHA_ENABLED=true` | [stack.env:55](scripts/docker/stack.env:55) (закоммичено `false`) | никто |
| Реальный keystore в `/app/ssl/keystore.p12` | генерируется самоподписанный | никто |
| Отключение сервиса `smtp` (smtp4dev) | в базовом compose | никто |

Итог: **развёртывание в прод — это ручная правка dev-файла**, где ни одно из перечисленных значений не защищено от забывчивости и не проверяется тестом. Именно это — корень большинства пунктов ниже.

---

## 2. Критичное (P0–P1)

| # | Pri | Проблема | Почему это плохо | Что сделать |
|---|---|---|---|---|
| 1 | **P0** | У `app` нет `healthcheck` ([compose:1-36](infrastructure/docker-compose.yaml:1-L36)); `/api/v1/health` — поверхностный, всегда `{"status":"UP"}` и не трогает БД/MinIO/Liquibase ([HealthCheckController:13-19](src/main/java/com/ukhanov/realhelpdesk/core/controller/HealthCheckController.java:13-L19)) | Оркестратор не знает, живо ли приложение: нет `depends_on: service_healthy` для app, нет rolling update, нет перезапуска по нездоровью. Под с упавшей БД продолжает рапортовать «UP» | Добавить `spring-boot-starter-actuator`, `management.endpoint.health.probes.enabled=true`, отдельные liveness и readiness (readiness — `db` + свой `HealthIndicator` для MinIO); **обязательно** внести пути в [WHITE_LIST_URLS](src/main/java/com/ukhanov/realhelpdesk/core/config/WhiteUrlConfig.java:12-L14), иначе `anyRequest().authenticated()` отдаст пробе `401`; в compose добавить `healthcheck` (bash в образе есть — на нём же entrypoint) |
| 2 | **P0** | Нет разведения dev/prod: один `stack.env`, `SPRING_PROFILES_ACTIVE=debug` закоммичен как дефолт ([stack.env:108](scripts/docker/stack.env:108)), а `smtp4dev` стоит в **базовом** compose ([compose:198-204](infrastructure/docker-compose.yaml:198-L204)) | Прод собирается из отладочных дефолтов; забытый профиль → DEBUG и SQL-логи в проде; `smtp4dev` в проде **принимает и проглатывает всю почту** — письма уходят в никуда без единой ошибки | Разложить на базу + оверлеи (§7); полезнее всего сделать **безопасный дефолт**: в базе `prod`, а `debug` включает только локальный override. Ошибка «слишком тихо» дешевле ошибки «слишком громко и не туда» |
| 3 | **P1** | Prod-инварианты не закреплены в коде: `CAPTCHA_ENABLED=false` ([:55](scripts/docker/stack.env:55)), `JWT_COOKIE_SAMESITE=None` ([:65](scripts/docker/stack.env:65)), `CORS_ALLOWED_ORIGINS=...localhost.ru` ([:162](scripts/docker/stack.env:162)), `JPA_SHOW_SQL`, `DB_MIGRATIONS_ENABLED`, `TRUST_PROXY_HEADERS` — всё env-driven с отладочными значениями | Прод может стартовать с выключенной капчей, `SameSite=None`, debug-origin'ом в CORS и без миграций — и ни одна проверка не сработает | Повторить приём, уже применённый к `ddl-auto=validate` ([application.properties:52](src/main/resources/application.properties:52)): закрепить инварианты в [application-prod.properties](src/main/resources/application-prod.properties) — `show-sql=false`, `liquibase.enabled=true`, `server.error.include-message=never`, `include-stacktrace=never`. Плюс prod-preflight по образцу [SameSiteTest](src/test/java/com/ukhanov/realhelpdesk/core/config/SameSiteTest.java), который **валит старт**, если в prod капча выключена, `SameSite=None` без домена cookie, в CORS есть `localhost` или keystore самоподписан |
| 4 | **P1** | TLS обслуживает само приложение, сертификат генерируется при старте с `CN=localhost` ([entrypoint.sh:31](scripts/docker/entrypoint.sh:31)), порт 8443 опубликован прямо на хост ([compose:8-9](infrastructure/docker-compose.yaml:8-L9)) | Самоподписанный сертификат клиенты отвергнут; на каждый рестарт новый отпечаток (tmpfs), pinning ломается; нет места для HSTS, gzip, статики, ограничения частоты на edge | В проде — терминация TLS на reverse-proxy (nginx/traefik/caddy), приложение по HTTP: `server.port=8080`, `server.forward-headers-strategy=framework` и **согласованно** `TRUST_PROXY_HEADERS=true` + `CLIENT_IP_FORWARDED_INDEX=-1` (именно для цепочки прокси, как в [stack.env:194-198](scripts/docker/stack.env:194-L198)). Либо монтировать реальный keystore в `/app/ssl/keystore.p12` (entrypoint это уже поддерживает) и падать в prod при обнаружении `CN=localhost` |
| 5 | **P1** | Нет CI: `make check` (spotless + checkstyle), `make verify`, e2e-скрипты и [`check-image.sh`](scripts/docker/check-image.sh) с trivy запускаются только вручную | Линтеры, тесты и сканы секретов не являются воротами — регрессии уезжают в прод | Пайплайн: `mvn -B verify` → `docker build` + `check-image.sh` + trivy (image/fs/config) + gitleaks → e2e на compose-стеке → SBOM (syft) и, при необходимости, подпись cosign |
| 6 | **P1** | `/tmp` — tmpfs без `size` ([compose:11-12](infrastructure/docker-compose.yaml:11-L12)), а GC-лог пишется **в файл** до `filecount=10,filesize=100M` = **до 1 ГБ** ([Dockerfile:78](Dockerfile:78)) при `mem_limit: 768m` и запрещённом swap (`memswap_limit` равен лимиту) | Страницы tmpfs учитываются в cgroup-лимите памяти: всплеск GC-логов способен выбить контейнер в OOM-kill. Отдельно: `JAVA_GC_LOG_OPTS` — **единственная** диагностика, оставленная включённой в Dockerfile, тогда как JFR/NMT/JMX/heap-dump выключены в [stack.env:227-230](scripts/docker/stack.env:227-L230) | Либо GC-лог в stdout (`-Xlog:gc*` без файла — ротацию возьмёт docker), либо `filecount=5,filesize=20M`; `tmpfs` ограничить `size=64m`. Решение по диагностике принять один раз и провести его через все `JAVA_*` |
| 7 | **P1** | В compose зашит `uid=999,gid=999` для `/app/ssl` ([compose:13](infrastructure/docker-compose.yaml:13)), а `spring-user` создаётся **без** явного uid ([Dockerfile:33-38](Dockerfile:33-L38)) | uid системного пользователя назначает образ и он сместится при смене базы → tmpfs окажется чужим → keystore не создастся → контейнер не стартует | Задать uid детерминированно (`useradd -r -u 10001 ...`) и те же значения в `tmpfs: /app/ssl:rw,uid=10001,gid=10001` |

---

## 3. Гигиена Docker / Compose (P2–P3)

| # | Pri | Проблема | Почему это плохо | Что сделать |
|---|---|---|---|---|
| 8 | P2 | Незапиннутые образы: `rnwood/smtp4dev` **без тега** (= `latest`, [compose:199](infrastructure/docker-compose.yaml:199)); `pgsty/silo:RELEASE.2026-09-16T00-00-00Z` — тег с нулевым временем выглядит синтетическим ([compose:39](infrastructure/docker-compose.yaml:39)); дайджестов нет нигде; `maven:3.9.6` — 2024 год | Сборка невоспроизводима: завтра `latest` принесёт другое поведение; несуществующий тег silo уронит `up` | Запинить тег + дайджест (`postgres:18@sha256:…`), проверить существование тега silo, поднять `maven` до актуального 3.9.x, подключить Renovate/Dependabot |
| 9 | P2 | Нет ротации логов docker: json-file без `max-size` | Приложение пишет только в stdout (это правильно, 12-factor) → `docker logs` — единственный канал, и без лимита он забивает диск хоста | `logging: {driver: json-file, options: {max-size: 10m, max-file: 5}}` через общий якорь `x-logging`; при необходимости — отправка в Loki/journald |
| 10 | P2 | `smtp4dev` опубликован на **все** интерфейсы: `"3000:80"` ([compose:203](infrastructure/docker-compose.yaml:203)) — единственный порт без `127.0.0.1`, в отличие от postgres и minio | Консоль smtp4dev с перепиской доступна из сети | `127.0.0.1:3000:80`; сам сервис перенести в локальный оверлей |
| 11 | P2 | `container_name` у всех сервисов ([compose:7](infrastructure/docker-compose.yaml:7) и далее) | Блокирует `--scale`, мешает двум стекам (dev и prod) на одном хосте; имя проекта берётся от каталога compose-файла → тома называются `infrastructure_postgres_data` | Убрать `container_name`, задать top-level `name: realhelpdesk` или `COMPOSE_PROJECT_NAME` |
| 12 | P2 | `restart: on-failure:2` у долгоживущих сервисов ([compose:36](infrastructure/docker-compose.yaml:36)) | После двух падений сервис остаётся лежать — тихий отказ | Для сервисов — `unless-stopped` + healthcheck + алертинг |
| 13 | P2 | Нет сетей и сегментации; у `smtp` нет healthcheck, поэтому `depends_on: service_started` ([compose:32-33](infrastructure/docker-compose.yaml:32-L33)) | БД и MinIO доступны любому сервису стека; app стартует, не дождавшись SMTP | Определить сеть `backend` (app, postgres, minio, smtp), добавить healthcheck smtp4dev и `condition: service_healthy` |
| 14 | P2 | `env_file` подаёт **весь** `stack.env` каждому сервису ([compose:20-21](infrastructure/docker-compose.yaml:20-L21)) | MinIO и Postgres получают `JAVA_*`, `SPRING_PROFILES_ACTIVE`, `PORT`, `SHARD_DB*`; app получает шардинг-ключи. Ошибка в `PORT`/`SHARD_DB` выглядит как конфиг приложения | Разделить на `stack.env` (инфраструктура) и `app.env` (приложение) либо перечислить `env_file` по сервисам |
| 15 | P2 | Секреты передаются переменными окружения ([compose:22-28](infrastructure/docker-compose.yaml:22-L28)) | Видны в `docker inspect` и `/proc/<pid>/environ`. Проект уже избегает этого для keystore (пароль не попадает в `docker history`) — здесь шаг не сделан | Compose `secrets:` (файлы в `/run/secrets`) + `spring.config.import=optional:configtree:/run/secrets/`. Оговорка: `configtree` мапит **имена файлов** в свойства, поэтому файлы придётся назвать как `jwt.secret-for-gen-jwt`; альтернатива — конвенция `*_FILE` |
| 16 | P2 | Нет OCI-меток и версии: `image: realhelpdesk:latest` ([compose:6](infrastructure/docker-compose.yaml:6)) | Нельзя сопоставить работающий контейнер с коммитом | `--build-arg GIT_SHA` → label `org.opencontainers.image.revision` (+ `source`, `created`), тег по git-описанию, в проде — ссылка на дайджест |
| 17 | P3 | У `smtp` нет `mem_limit`, у postgres нет `shm_size` | Дефолтные 64 МБ `/dev/shm` мешают параллельным планам Postgres; smtp ничем не ограничен | Добавить `mem_limit` и `shm_size: 256mb` |
| 18 | P3 | Нет `HEALTHCHECK` в Dockerfile и нет `-B` в `mvn package` ([Dockerfile:13](Dockerfile:13)) | Healthcheck уместнее в compose (образ переиспользуем), а цветной вывод Maven в логах сборки мешает CI | Healthcheck — в compose; `mvn -B -q package -DskipTests` |

---

## 4. Конфигурация Spring (P2–P3)

| # | Pri | Проблема | Почему это плохо | Что сделать |
|---|---|---|---|---|
| 19 | **P1** | Нет graceful shutdown, при этом в системе **25-секундный long polling** на `DeferredResult` и `@Scheduled`-задача | SIGTERM при деплое рвёт ожидающие запросы; планировщик не дожидается завершения и может прервать транзакцию повтора | `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`, `spring.task.scheduling.shutdown.await-termination=true` (по умолчанию планировщик не дожидается) |
| 20 | P2 | Hikari не настроен: нет `maximum-pool-size`, `minimum-idle`, `connection-timeout`, `leak-detection-threshold` | Пул из 10 соединений (дефолт) на 768 МБ heap — узкое место, особенно с найденными N+1 из [n1.md](n1.md); утечка соединения не диагностируется | Задать явно (пример в §7) |
| 21 | P2 | Нет `hibernate.jdbc.batch_size`, `order_inserts`/`order_updates`, `default_batch_fetch_size` | Это конфигурационный «ремонт» для проблем из [n1.md](n1.md): батч-фетч склеит N+1 по EAGER-коллекциям, батч-инсерты помогут рассылке оповещений | Задать в `application.properties` (пример в §7) |
| 22 | P2 | Нет actuator/Micrometer → нет метрик (пул, число и латентность запросов) | Состояние приложения можно оценить только включив SQL-логи | Вместе с п. 1: метрики Hikari/Hibernate/HTTP в формате Prometheus |
| 23 | P2 | `DB_MIGRATIONS_ENABLED` управляется окружением ([stack.env:142](scripts/docker/stack.env:142)) | Прод можно развернуть без миграций — `validate` упадёт, либо новые changeset'ы не применятся | В prod закрепить `spring.liquibase.enabled=true`, env-переключатель оставить для тестов |
| 24 | P3 | Относительный путь keystore: `KEY_STORE_PATH=file:ssl/keystore.p12` ([stack.env:111](scripts/docker/stack.env:111)) | Зависит от WORKDIR | Заменить на `file:/app/ssl/keystore.p12` |
| 25 | P3 | Мёртвая зависимость `telegrambots` 6.9.7.1 ([pom.xml:120-124](pom.xml:120-L124)): в коде **ни одного** использования, зато есть мёртвый `isMyTelegramBot` с пустым токеном ([WhiteUrlConfig:16-20](src/main/java/com/ukhanov/realhelpdesk/core/config/WhiteUrlConfig.java:16-L20)) | Лишний вес образа и транзитивные зависимости; пустой токен в security-коде вводит в заблуждение | Удалить зависимость и метод. Бонус: снимается непроверенный риск «второго SLF4J-биндинга» из аудита логирования |

---

## 5. Шардирование: отдельный разбор

Конфигурация честная и подробно документированная: прямо сказано, что co-location нет, FK разъедутся по шардам, а PK придётся переводить на генератор прокси ([database-sharding.yaml:16-25](infrastructure/shardingsphere/conf/database-sharding.yaml:16-L25)). Это редкость и плюс проекту. Но есть ловушка и три замечания:

| # | Pri | Проблема | Что сделать |
|---|---|---|---|
| 26 | **P1** | Схема в шарды попадает через `/docker-entrypoint-initdb.d/001-baseline-schema.sql` ([compose:164](infrastructure/docker-compose.yaml:164), [:188](infrastructure/docker-compose.yaml:188)), а init-скрипты Postgres выполняются **только на пустом томе**. Значит шарды навсегда останутся на baseline и не получат ни одного последующего changeset Liquibase — расхождение схемы с основной БД гарантировано | Гонять Liquibase по каждому шарду (или через прокси) отдельным шагом; либо генерировать init-SQL из полного changelog; плюс тест, сравнивающий схему шарда и основной БД |
| 27 | P2 | `sslmode=disable` между прокси и шардами ([database-sharding.yaml:34](infrastructure/shardingsphere/conf/database-sharding.yaml:34)) | Внутри compose-сети приемлемо, но решение стоит зафиксировать комментарием (как сделано для остальных компромиссов) |
| 28 | P2 | `GenerationType.IDENTITY` в entity против `keyGenerateStrategy: snowflake` в прокси | Проверить отдельно: как поведёт себя Hibernate, ожидающий сгенерированный ключ, когда id подставляет прокси. Это же смыкается с батч-инсертами (§4 п. 21) |
| 29 | P3 | Шардирование живёт профилем в общем compose-файле | Вынести в `docker-compose.sharding.yaml`: тогда «прод — это одна БД» становится однозначным, а не зависящим от `COMPOSE_PROFILES` |

---

## 6. Что уже сделано правильно (не трогать)

| Решение | Где | Почему это правильный инженерный ход |
|---|---|---|
| Минимальные привилегии контейнера | [compose:10-17](infrastructure/docker-compose.yaml:10-L17), [Dockerfile:33-60](Dockerfile:33-L60) | `read_only`, `cap_drop: ALL`, `no-new-privileges`, non-root пользователь, tmpfs для записываемых каталогов |
| Секреты не попадают в образ | [entrypoint.sh:23-32](scripts/docker/entrypoint.sh:23-L32), [check-image.sh](scripts/docker/check-image.sh) | Keystore генерируется на старте, пароль уходит в `keytool` через `:env` (не виден в `ps`), а `check-image.sh` проверяет `docker history`, `Config.Env`, ФС и jar на утечки |
| Fail-fast конфигурация в три слоя | [compose:23-28](infrastructure/docker-compose.yaml:23-L28), [application.properties](src/main/resources/application.properties), [entrypoint.sh:17-18](scripts/docker/entrypoint.sh:17-L18) | `${VAR:?}` в compose, `${VAR}` без дефолтов в properties, `: "${VAR:?}"` в entrypoint'ах + тест парности схемы `ApplicationConfigSchemaTest`. Тихих подстановок нет |
| `.dockerignore` с учётом семантики | [.dockerignore:6-17](.dockerignore:6-L17) | Явно объяснено, почему для секретов нужен префикс `**/`, и отсечены `.env`, ключи, `target/`, `.git/` |
| Multi-stage сборка + слои jar + кэш `~/.m2` | [Dockerfile:3-57](Dockerfile:3-L57) | Пересборка при правке кода не тянет зависимости; кэш BuildKit переживает изменение `pom.xml` |
| Healthchecks у postgres и minio, `condition: service_healthy` | [compose:29-35](infrastructure/docker-compose.yaml:29-L35), [:53-58](infrastructure/docker-compose.yaml:53-L58) | Гонки старта «приложение раньше БД» нет |
| Порты инфраструктуры только на localhost | [compose:44-45](infrastructure/docker-compose.yaml:44-L45), [:83](infrastructure/docker-compose.yaml:83) | БД, MinIO и консоль не торчат в сеть |
| `memswap_limit` равен `mem_limit` | [compose:18-19](infrastructure/docker-compose.yaml:18-L19) | Своп отключён — поведение предсказуемо, OOM не растягивается на диск |
| `ddl-auto=validate` закреплён в коде | [application.properties:52](src/main/resources/application.properties:52) | Изменяющий схему режим нельзя вернуть через окружение — образец для остальных инвариантов (§2 п. 3) |
| Комментарий про PGDATA в Postgres 18 | [compose:62-66](infrastructure/docker-compose.yaml:62-L66) | Захвачено реальное знание: монтирование на `/data` ломает образ 18 |
| Логи только в stdout, уровни — профилями | [logback-spring.xml](src/main/resources/logback-spring.xml) | 12-factor: контейнер не пишет файлы, сбор логов — задача платформы |

---

## 7. Целевая раскладка и примеры

```
infrastructure/
  docker-compose.yaml            # база: app + postgres + minio; безопасные дефолты (prod)
  docker-compose.override.yaml   # локально: smtp4dev, порты на хост, debug-профиль
  docker-compose.prod.yaml       # prod: реальный SMTP, secrets, unless-stopped, дайджесты
  docker-compose.sharding.yaml   # шардирование отдельным файлом, а не профилем
  .env.example                   # секреты (как сейчас)
scripts/env/
  stack.env                      # общие несекретные дефолты
  stack.local.env                # SPRING_PROFILES_ACTIVE=debug, smtp, CORS localhost
  stack.prod.env                 # SPRING_PROFILES_ACTIVE=prod, реальные домены
```

**Health-пробы (п. 1):**

```yaml
# infrastructure/docker-compose.yaml, сервис app
healthcheck:
  test: ["CMD-SHELL", "bash -c 'echo > /dev/tcp/127.0.0.1/8443'"]
  interval: 15s
  timeout: 5s
  retries: 5
  start_period: 60s   # Liquibase + прогрев Spring
```

```java
// WhiteUrlConfig.WHITE_LIST_URLS — иначе проба получит 401
"/actuator/health/**"
```

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
```

**Ротация логов и tmpfs (п. 6, 9):**

```yaml
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  app:
    logging: *default-logging
    tmpfs:
      - /tmp:size=64m
      - /app/ssl:rw,uid=10001,gid=10001
```

**Инварианты рабочего контура (п. 3, 19) — `application-prod.properties`:**

```properties
# Логирование (как сейчас)
logging.level.root=INFO
logging.level.com.ukhanov.realhelpdesk=INFO

# Инварианты, которые нельзя вернуть окружением — по образцу ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.liquibase.enabled=true
server.error.include-message=never
server.error.include-stacktrace=never

# Корректное завершение: в системе long polling на 25 секунд и планировщик
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
spring.task.scheduling.shutdown.await-termination=true
```

**Пул, батчи и метрики (п. 20-22) — `application.properties`:**

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=5000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=20000

# Смыкается с n1.md: склеивает N+1 по EAGER-коллекциям и батчит вставки
spring.jpa.properties.hibernate.default_batch_fetch_size=50
spring.jpa.properties.hibernate.jdbc.batch_size=30
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

Бонус к пулу: `reWriteBatchedInserts=true` в JDBC-URL (переписывает батч-инсерты в один многострочный `INSERT`) — прямо адресует N вставок из [n1.md](n1.md) п. 8.

**Секреты (п. 15):**

```yaml
secrets:
  jwt_secret:
    file: ./secrets/jwt.secret-for-gen-jwt   # имя файла = имя свойства для configtree
services:
  app:
    secrets: [jwt_secret]
```

```properties
spring.config.import=optional:configtree:/run/secrets/
```

---

## 8. План внедрения

| Шаг | Что | Пункты | Результат |
|---|---|---|---|
| 1 | Развести окружения: база + override + prod, безопасный дефолт профиля | 2, 3 | Прод перестаёт зависеть от ручной правки dev-файла |
| 2 | Health-пробы, graceful shutdown, dependency-check для MinIO | 1, 19 | Оркестрация и деплой без обрыва long polling |
| 3 | CI: тесты, линтеры, сканы, e2e | 5 | Регрессии не доезжают до прода |
| 4 | Память и логи: tmpfs со `size`, GC-лог в stdout или с малой ротацией, ротация docker logs | 6, 9 | Контейнер перестаёт рисковать OOM и диск хоста |
| 5 | TLS: прокси или реальный keystore + согласованные `TRUST_PROXY_HEADERS`/индекс | 4 | Клиенты не отвергают сертификат |
| 6 | Пины образов, uid, `name`, `container_name`, сети, secrets | 7, 8, 11-17 | Воспроизводимость и предсказуемость стека |
| 7 | Пул, батчи, метрики, инварианты Liquibase, чистка telegrambots | 20-25 | Наблюдаемость и меньше скрытых режимов |
| 8 | Шардирование: миграции в шарды, отдельный compose | 26-29 | Включение шардирования перестаёт быть минным полем |

---

## 9. Как проверять (ничего не запуская)

| Проверка | Команда | Что показывает |
|---|---|---|
| Валидность и результат интерполяции compose | `docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml config` | Неразрешённые `${VAR}`, итоговые значения, отсутствие секретов в рендере |
| Полный стек с шардированием | `docker compose -f infrastructure/docker-compose.yaml -f infrastructure/docker-compose.override.yaml -f infrastructure/docker-compose.sharding.yaml config --services` | Какие сервисы реально попадут в запуск (или `make ps` после `make up-sharding`) |
| Линт Dockerfile и entrypoint'ов | `hadolint Dockerfile`, `shellcheck scripts/docker/*.sh infrastructure/shardingsphere/entrypoint.sh` | Опасные конструкции, отсутствие `exec`, кавычки |
| Линт compose | `yamllint infrastructure/docker-compose.yaml` | Отступы, дубликаты ключей |
| Дайджесты и слои образов | `docker image inspect --format '{{.RepoDigests}}' <image>` | Что именно закреплено |
| Соответствие схемы конфигов и `stack.env` | `make test` (`ApplicationConfigSchemaTest`) | Парность schema/stack.env/compose; литералы — в `NON_SCHEMA_KEYS` |

---

## 10. Чек-лист

- [x] **P0** `healthcheck` у `app` + actuator (liveness/readiness, MinIO-indicator) + пути в `WHITE_LIST_URLS`
- [x] **P0** Развести dev/prod: `docker-compose{,.override,.prod}.yaml`, `stack.local.env`/`stack.prod.env`, безопасный дефолт профиля
- [x] **P0** Убрать `smtp4dev` из базового compose
- [x] **P1** Prod-preflight: капча, `SameSite` + домен cookie, CORS без `localhost`, запрет `CN=localhost`, обязательные миграции
- [x] **P1** CI: `mvn -B spotless:check checkstyle:check test`, hadolint, trivy, gitleaks, e2e (GitHub Actions)
- [x] **P1** `tmpfs size=64m`; GC-лог в stderr; решение по всем `JAVA_*` единое (JFR по умолчанию off)
- [x] **P1** Детерминированный uid (10001) в Dockerfile и в `tmpfs` compose
- [x] **P1** Шарды: Liquibase применяется к каждому шарду (`make migrate-shard`, liquibase-maven-plugin)
- [x] **P1** `server.shutdown=graceful` + `await-termination` для планировщика
- [x] **P2** Пины образов по тегам (Renovate следит); ротация docker logs; `127.0.0.1:3000`
- [x] **P2** `name: realhelpdesk`, снять `container_name`, `unless-stopped`, сеть `backend` (healthcheck smtp не нужен — app больше не depends_on smtp)
- [x] **P2** Разделить env-файлы по сервисам (env_file только у app); секреты через `secrets` + `configtree` — **отложено** (см. ревизию 2)
- [x] **P2** OCI-метки и версия образа из git (`org.opencontainers.image.revision` = GIT_SHA)
- [x] **P2** Hikari, батч-фетч/батч-инсерты, `reWriteBatchedInserts`, метрики (prometheus)
- [x] **P3** Абсолютный путь keystore; удалить `telegrambots` и `isMyTelegramBot`; `shm_size`; `mvn -B`
- [x] **P3** Вынести шардирование в отдельный compose-файл

---

## 11. Ревизия 2 — реализация

Внедрено по плану §8; тесты: **471 зелёные** (`mvn test`), `spotless:check` + `checkstyle:check` чистые, `docker compose ... config` валиден для всех трёх наборов файлов (dev/prod/sharding). Реализация: base `infrastructure/docker-compose.yaml` + `docker-compose.override.yaml` (локаль) + `docker-compose.prod.yaml` + `docker-compose.sharding.yaml`; Makefile использует явные `-f`-наборы (`COMPOSE_DEV`/`COMPOSE_PROD`/`COMPOSE_SHARDING`) — автоподхват override отключён.

### Решения и отклонения от первоначального плана

| # | Тема | Решение |
|---|---|---|
| 1 | TLS (п. 4) | Оставлен в приложении (keystore), не в nginx: `KEY_STORE_PATH=file:/app/ssl/keystore.p12` (абсолютный), prod-preflight (`ProdPreflightValidator`) валит старт при CN=localhost/нечитаемом keystore. e2e и локальный стек не ломаются |
| 2 | Secrets/configtree (п. 15) | **Отложено** (решение заказчика): секреты по-прежнему через `${VAR:?}` из `infrastructure/.env` |
| 3 | Пины образов (п. 16) | По тегам, не по дайджесту: дайджест ломает автобновления Renovate. Закреплены `postgres:18`, `apache/shardingsphere-proxy:5.5.3`, `rnwood/smtp4dev:3.15.0`, `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`, `hadolint:2.12.0`, `trivy:0.58.2`, `gitleaks:v8.21.2`; `renovate.json` добавлен |
| 4 | healthcheck smtp (п. 12) | Не делался: app больше не `depends_on` smtp (smtp только в локальном override), поэтому проба не защищает ничего критичного |
| 5 | `POSTGRES_USER`/`POSTGRES_DB` | Перенесены из `stack.env` в `environment` compose с дефолтами `${VAR:-user}`/`${VAR:-desk}` (переопределяются `infrastructure/.env`); `PORT`/`SHARD_DB`/`SHARD_DB_USER` задаются в sharding-файле compose |
| 6 | CI | gh-actions workflow (`.github/workflows/ci.yml`): build (spotless/checkstyle/test/package), docker (hadolint/trivy/gitleaks), e2e (секреты генерируются в CI, `make up` + ожидание `/actuator/health` + `make e2e-all`) |
| 7 | Boot 4 | Health-API живёт в `org.springframework.boot.health.contributor` (модуль `spring-boot-health`) — не в `org.springframework.boot.actuate.health` |
| 8 | Тесты и `prod` | Тесты активируют `prod` (`src/test/resources/application.properties`), поэтому закрепление `spring.liquibase.enabled=true` в `application-prod.properties` сломало бы слайс-тесты: добавлен затеняющий `src/test/resources/application-prod.properties` (`enabled=false`) |
| 9 | Парность конфигов | Литералы `application.properties` (Hikari, батчи, graceful, actuator) перечислены в `NON_SCHEMA_KEYS` `ApplicationConfigSchemaTest`; `stack.env` остался единственным несекретным источником env-значений |

### Что проверено

- `mvn test` — 471 тест, 0 ошибок (включая новые `ProdPreflightValidatorTest`, 7 сценариев).
- `mvn spotless:check checkstyle:check` — чисто.
- `docker compose -f ... config --quiet` — dev, prod и sharding наборы валидны (с заполненными секретами).
- `ApplicationConfigSchemaTest` — парность схемы и `stack.env`/compose проходит после всех правок.
