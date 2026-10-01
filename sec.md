# Сборка, конфигурация и секреты

Полное описание того, как проект собирается в Docker-образ, откуда берутся
значения конфигурации и как устроены секреты.

Связанные документы: [`README.md`](README.md) — запуск и использование,
[`docs/jwt-audit.md`](docs/jwt-audit.md) — аудит JWT,
[`docs/code-style.md`](docs/code-style.md) — стиль кода.

---

## 1. Три слоя: схема, конфиг, секреты

Главный принцип: **одно значение — один источник**. Всё остальное место
содержит только ссылку на него.

| Слой | Что в нём | Файл | В git |
|---|---|---|---|
| **Схема** (контракт) | какие переменные существуют и куда они идут. Ни одного значения, ни одного дефолта | `src/main/resources/application.properties` | да |
| **Конфиг окружения** | значения не-секретов: порты, хосты, лимиты, названия | `docker-compose.yaml` → `services.app.environment` | да |
| **Секреты** | пароли и ключи | `.env` (шаблон — `.env.example`) | **нет** (`.gitignore`) |

Второй и третий слой подставляются в контейнер как переменные окружения.
Тест `ApplicationConfigSchemaTest` не даёт слоям разойтись:

- значения в `application.properties` обязаны быть ровно `${VAR}` — без
  дефолта после `:` и без литерала до/после;
- список переменных схемы обязан совпадать со списком ключей
  `services.app.environment` **один в один** (сверяется в тесте, а не вручную).

Если добавить ключ в `application.properties` и забыть про compose —
упадёт `mvn test`. Если добавить значение в compose и забыть про схему —
упадёт там же.

Тест читает файл напрямую по пути `src/main/resources/application.properties`,
а не с classpath. Поэтому на результат не влияет
`src/test/resources/application.properties`, который перекрывает продовую
конфигурацию в тестовом класслоаде.

---

## 2. Сборка образа

### 2.1. Стадии

`Dockerfile` — двухстадийный.

**Стадия `build`** (`maven:3.9.6-eclipse-temurin-21`, JDK):

1. `COPY pom.xml` + `mvn dependency:go-offline` — отдельным слоем, чтобы
   кэшировались зависимости и пересборка не ходила в Maven Central при
   каждом изменении кода.
2. `COPY src` + `COPY config` — `config/` нужен, потому что в фазе
   `validate` гоняются Spotless и Checkstyle.
3. `mvn package -DskipTests` — **тесты в образ не попадают**, они гоняются
   на хосте. Линт при этом выполняется: Spotless и Checkstyle привязаны к
   фазе `validate`, а она входит в `package`.
4. Жирный jar переименовывается в `app.jar`.

**Стадия `runtime`** (`eclipse-temurin:21-jre`, только JRE):

1. Пользователь `spring-user` — контейнер работает **не от root**.
2. `mkdir /app/ssl` + `chown` — сюда при старте ляжет keystore.
3. `COPY --chown --chmod docker/entrypoint.sh`.
4. `COPY --from=build --chown ... app.jar` — слои сборочной стадии в образ не
   переносятся, `target/` в runtime-образе отсутствует.
   Права задаются здесь же: отдельный `chown -R /app` после `COPY` создавал
   слой **73.3 МБ** — полную копию jar только ради смены владельца.
5. `ENV JAVA_*` — флаги JVM (память, GC-лог, JFR, JMX, heap dump при OOM).
   Это конфигурация JVM, она живёт в образе и переопределяется
   переменными окружения. Часть из них пишет в `/tmp` — см. §7.
6. `EXPOSE 8443`, `ENTRYPOINT ["/app/entrypoint.sh"]`.

### 2.2. Чего в образе нет

| | Почему |
|---|---|
| `.env` и все 4 секрета | отсекается `.dockerignore` ещё до отправки контекста в daemon, а в Dockerfile не упоминается |
| `keystore.p12` и `KEY_STORE_PASS` | генерируется при старте контейнера, см. §4.3 |
| Значения конфигурации | они в `docker-compose.yaml`, а не в jar |
| Исходники, `target/`, тесты | только в стадии `build`, в runtime не переносятся |

Как это проверяется на самом деле:

```bash
# 1. Docker предупреждает, если ARG/ENV несёт чувствительное значение:
#    "SecretsUsedInArgOrEnv: Do not use ARG or ENV instructions for sensitive data".
#    Предупреждения нет — сборка секретов не касается.
docker build --no-cache . 2>&1 | grep -i SecretsUsedInArgOrEnv    # пусто

# 2. Имена секретов не должны встречаться ни в одной инструкции сборки.
#    (Значения build-arg'ов BuildKit раскрывает в истории: там видно
#     и "ARG SECRET=...", и "RUN |1 SECRET=... keytool ...".)
docker history --no-trunc realhelpdesk:latest \
  | grep -iE "JWT_SECRET|DB_PASSWORD|KEY_STORE_PASS|storepass"     # пусто

# 3. Переменные окружения итогового образа — только конфиг, без секретов.
docker inspect realhelpdesk:latest \
  --format '{{range .Config.Env}}{{println .}}{{end}}'

# 4. Файловая система контейнера: нет ни .env, ни *.p12.
docker run --rm --entrypoint sh realhelpdesk:latest \
  -c 'find / -xdev \( -name ".env" -o -name "*.p12" \) -not -path "/proc/*"'

# 5. Скан образа на секреты (trivy умеет ищущие правила).
trivy image --scanners secret realhelpdesk:latest
```

Раньше здесь была одна команда
`docker history --no-trunc | grep -iE "keytool|storepass"`. Сейчас она
**ничего не доказывает**: `keytool` переехал в `entrypoint.sh`, то есть в
сборке его команд больше нет вовсе, а про `JWT_SECRET`, `DB_PASSWORD` и
`MAIL_PASSWORD` она ничего не спрашивает.

### 2.3. Контекст сборки и `.dockerignore`

`Dockerfile` копирует только `pom.xml`, `src/`, `config/` и `docker/`, но
daemon получает **всю рабочую директорию** целиком. Без `.dockerignore` в
контекст вместе с кодом уходят `.env`, `target/`, `.git/` и `*.p12` — файлы
не попадают в слои образа, но уезжают по сети на демон и оседают в его
кэше.

`.dockerignore` отсекает: `.env`, `.env.*`, `*.p12`, `*.key`, `*.pem`,
`*.jks`, `ssl/`, `target/`, `.git/`, `docs/`, `scripts/`, `*.md`, `*.log`.

Проверка: `docker build` не должен ругаться на отсутствующие
`src`/`pom.xml`/`docker`, а `find /` в контейнере из §2.2 — ничего не
находить.

### 2.4. `docker build` против `docker compose build`

Это разные вещи, и разница важна:

| Команда | Без `.env` |
|---|---|
| `docker build .` | **проходит** — Dockerfile от секретов не зависит вовсе |
| `docker compose build` | **падает** — Compose интерполирует `${...}` во **всём** файле до выполнения любой команды, включая `build` |

Это не утечка: секрет из интерполяции уходит только в
`services.app.environment` контейнера, в слои образа он не попадает.
Это fail-fast: без заполненного `.env` не работает ни одна команда compose.

### 2.5. Сборка и проверки на хосте

```bash
mvn test      # unit- и слайс-тесты (@DataJpaTest) + Spotless + Checkstyle
mvn verify    # то же плюс интеграционные *IT (maven-failsafe)
```

Тестам Docker не нужен: у них собственная конфигурация в
`src/test/resources/application.properties`, которая перекрывает продовую
(тестовые ресурсы идут в classpath первыми). Продовая схема целиком
состоит из обязательных `${VAR}` и в тестовом окружении не задана —
без перекрытия контекст упал бы.

---

## 3. Как используется переменная окружения

### 3.1. Путь значения до приложения

```
.env  ──┐
        ├─► docker compose интерполирует  ─► environment контейнера
docker-compose.yaml ─┘                              │
                                                    ▼
                               JVM: переменная окружения
                                                    │
                                                    ▼
        application.properties:  spring.mail.host=${MAIL_HOST}
                                                    │
                                                    ▼
                    Spring резолвит ${...}  ─►  свойство конфигурации
```

Файл `application.properties` **не хранит значений** — только ссылки.
Spring резолвит их из окружения контейнера. Если переменной нет,
резолв бросает `Could not resolve placeholder 'MAIL_HOST'` и приложение
не стартует. Тихих подстановок нет.

### 3.2. Где что править

| Что меняется | Файл | Команда |
|---|---|---|
| Порог рейт-лимита | `docker-compose.yaml` → `RATE_LIMIT_*` | `docker compose up -d` |
| Капча вкл/выкл | `docker-compose.yaml` → `CAPTCHA_ENABLED` | `docker compose up -d` |
| `X-Forwarded-For` | `docker-compose.yaml` → `TRUST_PROXY_HEADERS` | `docker compose up -d` |
| Профиль логов | `docker-compose.yaml` → `SPRING_PROFILES_ACTIVE` | `docker compose up -d` |
| Почта, домен, название | `docker-compose.yaml` | `docker compose up -d` |
| Любой секрет | `.env` | `docker compose up -d` |
| Новый ключ конфига | сначала `application.properties`, потом `docker-compose.yaml` | `mvn test` (сверка) |
| Тексты и темы писем | `src/main/resources/messages.properties` | пересборка (файл внутри jar) |
| Уровни логирования | `application-debug.properties` / `application-prod.properties` | пересборка |
| Флаги JVM | `Dockerfile` → `ENV JAVA_*` | пересборка |

Пересборка образа не нужна почти никогда: значения приходят в контейнер
снаружи. Пересборка требуется, когда меняется то, что запечено в jar
или в слоях образа.

### 3.3. Профили

Два профиля — `debug` и `prod`. Они влияют **только на уровни логирования**
(файлы `application-debug.properties`, `application-prod.properties`);
всё остальное живёт в compose.

`SPRING_PROFILES_ACTIVE` обязателен — дефолта в схеме нет. Локальный compose
задаёт `debug`. Переключение — правка одной строки и пересоздание контейнера:

```yaml
# docker-compose.yaml, services.app.environment
SPRING_PROFILES_ACTIVE: prod
```
```bash
docker compose up -d     # пересоздать контейнер, пересборка не нужна
```

Профиль читается при старте приложения, поэтому «на лету» он не
переключается в любом случае — нужен рестарт контейнера. Внутрь контейнера
заходить для этого не требуется.

---

## 4. Секреты

### 4.1. Полный список

| Переменная | Что это | Где используется | Пустое значение |
|---|---|---|---|
| `JWT_SECRET` | подпись и проверка JWT (HS256, base64, минимум 32 байта) | `jwt.secret-for-gen-jwt` | запрещено |
| `DB_PASSWORD` | пароль PostgreSQL, общий для приложения и БД | `spring.datasource.password` и `POSTGRES_PASSWORD` | запрещено |
| `KEY_STORE_PASS` | пароль keystore с приватным ключом TLS | `server.ssl.key-store-password`, генерация keystore | запрещено |
| `MAIL_PASSWORD` | пароль SMTP | `spring.mail.password` | **допустимо** — smtp4dev работает без аутентификации |

Шаблон — `.env.example`. Рабочий `.env` в git не попадает (`.gitignore`),
права на файл лучше держать `600`.

### 4.2. Как секрет попадает в контейнер

```yaml
# docker-compose.yaml
JWT_SECRET: "${JWT_SECRET:?JWT_SECRET is required - copy .env.example to .env}"
MAIL_PASSWORD: "${MAIL_PASSWORD-}"
```

Синтаксис надо знать точно, иначе из него следует не то, что кажется:

- `${VAR:?сообщение}` — Compose **падает сразу**, если переменной нет
  **или она пустая**. Пустая строка тоже ошибка.
- `${VAR-}` — подставляется **всегда**: переменная не задана → пустая строка,
  задана (даже пустая) → её значение. **Ни при каких условиях не падает.**

У `MAIL_PASSWORD` этим синтаксисом задан явный пустой дефолт — это
единственное исключение в проекте. `${VAR:?}` здесь неприменим: он уронил бы
запуск на пустом пароле, а smtp4dev аутентификацию не требует. Остальные три
секрета обязаны прийти из `.env`, иначе запуск не состоялся.

### 4.3. Keystore и его пароль

Keystore **не создаётся при сборке**. Раньше `KEY_STORE_PASS` был build-arg:
Docker подставляет build-arg в `RUN` ещё на этапе парсинга инструкций, и
реальный пароль оседал в слоях образа. BuildKit дополнительно предупреждает
об этом строкой
`SecretsUsedInArgOrEnv: Do not use ARG or ENV instructions for sensitive data`,
а значение остаётся и в `docker history --no-trunc` — видно и
`ARG SECRET=...`, и `RUN |1 SECRET=... keytool ...`.

Сейчас генерация выполняется в `docker/entrypoint.sh` при старте контейнера:

1. Если `/app/ssl/keystore.p12` отсутствует — создаёт самоподписанный
   сертификат через `keytool`, пароль берётся из окружения.
2. Если файл **смонтирован извне** (прод: свой сертификат) — генерация
   пропускается, используется монтированный файл.
3. Без `KEY_STORE_PASS` контейнер падает с внятной ошибкой.
4. Дальше `exec java ...` — флаги JVM совпадают с прежними байт в байт.

Два следствия, которые легко пропустить:

- **Смонтированный keystore несёт собственный пароль.** Менять
  `KEY_STORE_PASS` в `.env` бессмысленно — сверяется пароль самого файла.
  Несовпадение валит старт Tomcat.
- **Самоподписанный сертификат пересоздаётся вместе с контейнером.**
  Всё содержимое `/app/ssl` живёт в слое контейнера, поэтому
  `docker compose up -d --force-recreate` (и любое пересоздание) даёт новый
  сертификат с новым отпечатком. Клиенты, прижавшие отпечаток, начнут
  ругаться. Для стабильности нужно монтировать keystore volume'ом.

Смена `KEY_STORE_PASS` для сгенерированного keystore требует пересоздания
контейнера (`docker compose up -d`), без пересборки образа.

### 4.4. Ротация

```bash
# JWT
openssl rand -base64 32        # → .env → JWT_SECRET
docker compose up -d

# пароль PostgreSQL: сначала в БД, потом в .env → DB_PASSWORD
docker compose up -d

# пароль SMTP
docker compose up -d

# keystore: новый пароль в .env → KEY_STORE_PASS
docker compose up -d
```

Ни одна ротация не требует пересборки образа, но у каждой есть цена:

- **`JWT_SECRET` инвалидирует все выданные токены.** После пересоздания
  контейнера каждый клиент получит 401 и перелогинится. Это массовый
  разлогин, а не «бесшовное обновление». Дежурный обход — выдавать новый
  ключ с `kid` и держать старый в наборе на время жизни токенов; сейчас
  этого нет, поэтому планируйте ротацию на «тихое» окно.
- **`DB_PASSWORD`: порядок важен.** Сначала `ALTER ROLE ... PASSWORD` в БД,
  потом `.env`. В обратном порядке приложение упадёт на первом же запросе.
- **`KEY_STORE_PASS`:** см. §4.3 — для смонтированного keystore `.env`
  вообще ни при чём.

### 4.5. Ограничение, о котором надо знать

Переменные окружения видны любому, у кого есть доступ к Docker:

```bash
docker inspect realhelpdesk --format '{{range .Config.Env}}{{println .}}{{end}}'
```

То есть секрет из `.env` прочитает тот, кто может смотреть контейнеры.
Для одного хоста с доверенными администраторами это приемлемо. Если нет —
уровни дальше по возрастанию:

| Уровень | Механизм | Когда |
|---|---|---|
| Локально | `.env`, gitignore, права 600 | всегда |
| Один хост, доверенные админы | текущая схема | **текущий случай** |
| Compose `secrets:` + Spring `configtree:` | секрет — файл, а не переменная окружения | когда `docker inspect` виден лишним |
| Kubernetes Secrets / Vault / SOPS + age | прод с ротацией и аудитом | кластер |

`secrets:` — дешёвый и почти бесплатный шаг, потому что Spring умеет
читать такой файл **без изменения схемы**. По документации Spring Boot,
в `configtree:` *имя файла становится ключом, а содержимое — значением*
(https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree).
Файл `DB_PASS` с содержимым `secret` даёт свойство `DB_PASS`, а в схеме уже
лежит `${DB_PASS}` — править `application.properties` не нужно.

Как это выглядело бы:

```yaml
services:
  app:
    secrets: [DB_PASS, JWT_SECRET, KEY_STORE_PASS]   # вместо этих ключей в environment
    environment:
      SPRING_CONFIG_IMPORT: "optional:configtree:/run/secrets/"
      MAIL_PASSWORD: "${MAIL_PASSWORD-}"             # остаётся, ей нужен пустой дефолт

secrets:
  DB_PASS:        { environment: DB_PASSWORD }
  JWT_SECRET:     { environment: JWT_SECRET }
  KEY_STORE_PASS: { environment: KEY_STORE_PASS }
```

Что это меняет и что придётся поправить:

- секреты перестают попадать в `docker inspect ... Config.Env`, но в
  обычном `docker compose` (не Swarm) они всё равно пишутся во временные
  файлы на хосте и монтируются в контейнер — на хосте они никуда не деваются;
- `SPRING_CONFIG_IMPORT` добавит в `services.app.environment` лишний ключ,
  которого нет в схеме, — тест `ApplicationConfigSchemaTest` (сверка
  «схема ↔ environment») упадёт и его придётся поправить: сравнивать
  `environment` + `secrets` либо завести список служебных ключей-исключений;
- `DB_PASSWORD` нужен и самой PostgreSQL — этот ключ остаётся
  в `environment` сервиса `postgres`, как и сейчас.

Поэтому переход — не «всего лишь замена секции», а правка compose **и**
теста. Ровно по этой причине переход пока не сделан.

### 4.6. Что запрещено

- секрет в git, в `Dockerfile`, в `docker-compose.yaml` **литералом**
  (только `${VAR:?}` из `.env`);
- секрет в логах, в исключениях, в URL запроса;
- `ARG`/`ENV` со значением секрета в `Dockerfile`;
- дефолт секрета (`${JWT_SECRET:какой-то-текст}`) — это скрытый пароль
  в репозитории;
- `*.p12`, `*.key`, `*.pem`, `ssl/` в git — отсекает `.gitignore`;
- `.env`, `*.p12` и т. п. в контексте сборки — отсекает `.dockerignore`.

---

## 5. Порядок запуска

```bash
cp .env.example .env
# заполнить JWT_SECRET (openssl rand -base64 32), DB_PASSWORD, KEY_STORE_PASS;
# MAIL_PASSWORD оставить пустым для smtp4dev

docker compose up --build -d
```

Что поднимается:

| Сервис | Образ | Порты |
|---|---|---|
| `app` | собирается из `Dockerfile` | `8443` → API (HTTPS) |
| `postgres` | `postgres:15` | `127.0.0.1:5432` |
| `smtp` | `rnwood/smtp4dev` | `127.0.0.1:25`, `3000` (веб) |

PostgreSQL и SMTP слушают только `localhost`. Данные БД — в томе
`postgres_data`.

Готовность: у `postgres` есть `healthcheck` (`pg_isready`), и `app` ждёт
`condition: service_healthy`, а не просто «контейнер запущен». Без этого
Hibernate мог успеть упасть до старта СУБД, а `restart: on-failure:2` давал
приложению только две попытки.

Политика перезапуска у всех сервисов — `on-failure:2`: две попытки, дальше
контейнер остаётся остановленным. См. §7.

---

## 6. Чек-лист при ревью

- [ ] Новое свойство в `application.properties` — ровно `${VAR}`, без дефолта.
- [ ] Для него добавлен ключ в `services.app.environment` (`mvn test` это
      проверит).
- [ ] Значение — литерал в compose, не `${VAR:-дефолт}`.
- [ ] Секрет — только в `.env`, в compose подставлен через `${VAR:?}`.
- [ ] Ничего нового не появилось в `Dockerfile` вида `ARG`/`ENV` со
      значением.
- [ ] Новые типы файлов (ключи, `.env`, логи) отражены в `.dockerignore`.
- [ ] Образ чист: `docker history --no-trunc | grep -iE
      "JWT_SECRET|DB_PASSWORD|KEY_STORE_PASS|storepass"` пуст,
      `trivy image --scanners secret` без находок.
- [ ] Изменение сконфигурировано без пересборки (или в README явно
      сказано, почему нужна пересборка).

---

## 7. Что явно не решено

Ограничения текущей схемы, которые надо держать в голове. Ни одно из них
не критично для доверенного одиночного хоста, но знать о них нужно.

### 7.1. JVM пишет артефакты в `/tmp` контейнера

| Файл | Откуда | Ограничение в образе |
|---|---|---|
| `/tmp/<pid>.hprof` | `-XX:+HeapDumpOnOutOfMemoryError` | нет |
| `/tmp/<имя>.jfr` | `-XX:StartFlightRecording` | `maxsize=10g`, `maxage=24h` |
| `/tmp/java_error.log` | `-XX:ErrorFile` | нет |
| `/tmp/gc.log*` | `-Xlog:gc*` | `filesize=100M`, `filecount=10` |

Эти файлы лежат в слое контейнера, их видит любой, у кого есть
`docker exec` или доступ к данным хоста. **Heap dump — это снимок памяти,
включая значения секретов, оказавшихся в куче.** JFR может дорасти до
10 ГБ в `/tmp`, где лежит и GC-лог.

Что сделать: в прод вынести `/tmp` в volume с квотой, отключить
`JAVA_JFR_OPTS` и/или `JAVA_HEAP_DUMP_OPTS`, либо ужать `maxsize`.

### 7.2. JMX без аутентификации

`JAVA_JMX_OPTS` включает `jmxremote.authenticate=false`,
`jmxremote.ssl=false`, порт 5005, `java.rmi.server.hostname=127.0.0.1`.
Порт **не** объявлен в `EXPOSE` и **не** опубликован в `ports:`, то есть с
хоста он недоступен. Но внутри контейнерной сети к нему может подключиться
любой соседний контейнер, а аутентификации нет.

Что сделать: в прод выставить `JAVA_JMX_OPTS=""` или включить
аутентификацию/SSL.

### 7.3. Нет healthcheck у `app`

`postgres` проверяется через `pg_isready`, у `app` healthcheck не описан —
`docker compose ps` и внешние мониторы не отличат живой процесс от зависшего.

### 7.4. `restart: on-failure:2`

Две попытки, дальше контейнер лежит остановленным. Сейчас это безопаснее
раньше, потому что гонка «app стартует раньше postgres» устранена
`condition: service_healthy`. Но если нужна самовосстановляемость —
менять на `unless-stopped`.

### 7.5. Образы по тегу, не по digest

`maven:3.9.6-eclipse-temurin-21`, `eclipse-temurin:21-jre`, `postgres:15`,
`rnwood/smtp4dev` — теги могут «уехать» вместе с апстримом. Для прода
нужен pin по digest + Renovate/Dependabot.

### 7.6. Сборка: кэш Maven и слои jar

`mvn dependency:go-offline` не использует BuildKit cache-mount, а jar
собирается жирным (в Dockerfile это задумано: при запуске через `java -jar`
слои Spring Boot не используются, поэтому `-Djarmode=tools extract` и не
нужен — команда `extract` в Boot 4.1.1 подтверждена). Это вопрос
**скорости сборки**, не безопасности.

### 7.7. Терминация TLS в приложении

Сейчас HTTPS поднимает сам Tomcat. Прод-альтернативы: Spring SSL bundles
(PEM-файлы, без keytool) или терминация на reverse proxy с отключением
HTTPS в приложении.
