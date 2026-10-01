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
Тест `EmailPropertiesTest` не даёт слоям разойтись:

- значения в `application.properties` обязаны быть ровно `${VAR}` — без
  дефолта после `:` и без литерала до/после;
- список переменных схемы обязан совпадать со списком ключей
  `services.app.environment` **один в один** (сейчас 50/50).

Если добавить ключ в `application.properties` и забыть про compose —
упадёт `mvn test`. Если добавить значение в compose и забыть про схему —
упадёт там же.

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
2. `mkdir /app/ssl` — сюда при старте ляжет keystore.
3. `COPY docker/entrypoint.sh` + `chmod +x`.
4. `COPY --from=build app.jar` — слои сборочной стадии в образ не
   переносятся, `target/` в runtime-образе отсутствует.
5. `chown -R spring-user:spring-group /app`.
6. `ENV JAVA_*` — флаги JVM (память, GC-лог, JFR, JMX, heap dump при OOM).
   Это конфигурация JVM, она живёт в образе и переопределяется
   переменными окружения.
7. `EXPOSE 8443`, `ENTRYPOINT ["/app/entrypoint.sh"]`.

### 2.2. Чего в образе нет

| | Почему |
|---|---|
| `.env` и все 4 секрета | в контекст сборки он не копируется, а в Dockerfile не упоминается |
| `keystore.p12` и `KEY_STORE_PASS` | генерируется при старте контейнера, см. §4 |
| Значения конфигурации | они в `docker-compose.yaml`, а не в jar |
| Исходники, `target/`, тесты | только в стадии `build`, в runtime не переносятся |

Проверяется так:

```bash
docker history --no-trunc realhelpdesk:latest | grep -iE "keytool|storepass"
# пусто
```

### 2.3. `docker build` против `docker compose build`

Это разные вещи, и разница важна:

| Команда | Без `.env` |
|---|---|
| `docker build .` | **проходит** — Dockerfile от секретов не зависит вовсе |
| `docker compose build` | **падает** — Compose интерполирует `${...}` во **всём** файле до выполнения любой команды, включая `build` |

Это не утечка: секрет из интерполяции уходит только в
`services.app.environment` контейнера, в слои образа он не попадает.
Это fail-fast: без заполненного `.env` не работает ни одна команда compose.

### 2.4. Сборка и проверки на хосте

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
| Уровни логгирования | `application-debug.properties` / `application-prod.properties` | пересборка |
| Флаги JVM | `Dockerfile` → `ENV JAVA_*` | пересборка |

Пересборка образа не нужна почти никогда: значения приходят в контейнер
снаружи. Пересборка требуется, когда меняется то, что запечено в jar
или в слоях образа.

### 3.3. Профили

Два профиля — `debug` и `prod`. Они влияют **только на уровни логгирования**
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

- `${VAR:?сообщение}` — Compose **падает сразу**, если переменной нет или она
  пустая. Проверяется до запуска контейнера.
- `${VAR-}` — падает только если переменной нет вовсе; пустое значение
  пропускается (нужно для `MAIL_PASSWORD`).

Ни одного дефолта нет: секрет либо пришёл из `.env`, либо запуск не состоялся.

### 4.3. Keystore и его пароль

Keystore **не создаётся при сборке**. Раньше `KEY_STORE_PASS` был build-arg,
Docker подставляет build-arg в `RUN` ещё на этапе парсинга инструкций, и
реальный пароль оседал в слоях образа — его видно в
`docker history --no-trunc`. Это документированное предупреждение Docker.

Сейчас генерация выполняется в `docker/entrypoint.sh` при старте контейнера:

1. Если `/app/ssl/keystore.p12` отсутствует — создаёт самоподписанный
   сертификат через `keytool`, пароль берётся из окружения.
2. Если файл **смонтирован извне** (прод: свой сертификат) — генерация
   пропускается, используется монтированный файл.
3. Без `KEY_STORE_PASS` контейнер падает с внятной ошибкой.
4. Дальше `exec java ...` — флаги JVM совпадают с прежними байт в байт.

Смена `KEY_STORE_PASS` требует только `docker compose up -d`, без пересборки.

### 4.4. Ротация

```bash
# JWT
openssl rand -base64 32        # → .env → JWT_SECRET
docker compose up -d

# пароль PostgreSQL: сменить в БД, затем в .env → DB_PASSWORD
docker compose up -d

# пароль SMTP
docker compose up -d

# keystore: новый пароль в .env → KEY_STORE_PASS
docker compose up -d
```

Ни одна ротация не требует пересборки образа.

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
| Swarm / Compose | `secrets:` — файл в контейнере, Spring читает `file:/run/secrets/...` | когда `docker inspect` виден лишним |
| Кластер | Kubernetes Secrets / Vault / SOPS + age | прод с ротацией и аудитом |

Переход на `secrets:` — это замена `environment:` на `secrets:` в compose и
переход свойств Spring на чтение из файла; схема `application.properties`
при этом не меняется.

### 4.6. Что запрещено

- секрет в git, в `Dockerfile`, в `docker-compose.yaml` **литералом**
  (только `${VAR:?}` из `.env`);
- секрет в логах, в исключениях, в URL запроса;
- `ARG`/`ENV` со значением секрета в `Dockerfile`;
- дефолт секрета (`${JWT_SECRET:какой-то-текст}`) — это скрытый пароль
  в репозитории;
- `*.p12`, `*.key`, `*.pem`, `ssl/` в git — отсекает `.gitignore`.

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

PostgreSQL и SMTP слушают только `localhost`. Данные БД — в томологии
`postgres_data`. Политика перезапуска — `on-failure:2`.

---

## 6. Чек-лист при ревью

- [ ] Новое свойство в `application.properties` — ровно `${VAR}`, без дефолта.
- [ ] Для него добавлен ключ в `services.app.environment` (`mvn test` это
      проверит).
- [ ] Значение — литерал в compose, не `${VAR:-дефолт}`.
- [ ] Секрет — только в `.env`, в compose подставлен через `${VAR:?}`.
- [ ] Ничего нового не появилось в `Dockerfile` вида `ARG`/`ENV` со
      значением.
- [ ] Изменение сконфигурировано без пересборки (или в README явно
      сказано, почему нужна пересборка).
