# Сборка, конфигурация и секреты

Полное описание того, как проект собирается в Docker-образ, откуда берутся
значения конфигурации и как устроены секреты.

Связанные документы: [`README.md`](README.md) — запуск и использование,
[`docs/jwt-audit.md`](docs/jwt-audit.md) — аудит JWT,
[`docs/code-style.md`](docs/code-style.md) — стиль кода.

---

## 1. Четыре слоя: схема, продукт, окружение, секреты

Главный принцип: **одно значение — один источник**. Каждое другое место
хранит только ссылку на него.

| Слой | Что в нём | Файл | В git |
|---|---|---|---|
| **Схема** (контракт) | какие переменные существуют и куда они идут. Ни одного значения, ни одного дефолта | `src/main/resources/application.properties` + `application-domains.properties` (домены) | да |
| **Продукт** | решения проекта: лимиты, капча, сроки токенов, брендинг, адреса писем | `docker/app.env` | да |
| **Конфиг окружения** | то, что зависит от хоста и развёртывания: порты, хосты, профиль, ресурсы, JVM | `docker-compose.yaml` → `services.app.environment` | да |
| **Секреты** | пароли и ключи | `.env` (шаблон — `.env.example`) | **нет** (`.gitignore`) |

Критерий разделения второго и третьего слоя: что заставляет значение
меняться. Смена хоста, сети, порта, SMTP-сервера, профиля — окружение
(compose). Решение продукта — лимиты, капча, тексты, названия — `app.env`.
Если значение меняется только вместе с правкой кода, ему вообще не место
в переменной окружения.

Последние три слоя подставляются в контейнер как переменные окружения:
`env_file` и `environment` compose дают ровно один набор, и приоритет
у `environment` (перекрывает `env_file`, §3.1). Тест
`ApplicationConfigSchemaTest` не даёт слоям разойтись:

- значения в файлах схемы (`application.properties` и
  `application-domains.properties`) обязаны быть ровно `${VAR}` — без
  дефолта после `:` и без литерала до/после (единственное исключение —
  `spring.config.import`: это адрес подключаемого файла, а не переменная);
- список переменных схемы обязан совпадать с объединением ключей
  `services.app.environment` и `docker/app.env` **один в один**
  (сверяется в тесте, а не вручную). Исключение — ключи, начинающиеся
  с `JAVA_`: они переопределяют `ENV` образа и приложению не нужны (§7.8);
- один и тот же ключ не должен быть задан в обоих файлах: тест падает
  и на дубль, и на пропуск.

Если добавить ключ в один из файлов схемы и забыть про значения —
упадёт `mvn test`. Если добавить значение в compose или `app.env` и
забыть про схему — упадёт там же.

Тест читает файлы схемы напрямую по пути `src/main/resources/...`,
а не с classpath. Поэтому на результат не влияет
`src/test/resources/application.properties`, который перекрывает продовую
конфигурацию в тестовом класслоаде.

---

## 2. Сборка образа

### 2.1. Стадии

`Dockerfile` — двухстадийный.

**Стадия `build`** (`maven:3.9.6-eclipse-temurin-21`, JDK):

1. `COPY pom.xml`, `COPY src`, `COPY config` — `config/` нужен, потому что в
   фазе `validate` гоняются Spotless и Checkstyle.
2. `RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests` —
   **тесты в образ не попадают**, они гоняются на хосте, а зависимости
   оседают в кэше BuildKit, а не в слое образа: кэш переживает любую правку
   `pom.xml`, поэтому отдельный `mvn dependency:go-offline` не нужен.
   Линт при этом выполняется: Spotless и Checkstyle привязаны к фазе
   `validate`, а она входит в `package`.
3. jar переименовывается в `app.jar`.
4. `java -Djarmode=tools -jar app.jar extract --layers --destination
   /app/extracted` — jar раскладывается на слои Spring Boot (§7.6).

**Стадия `runtime`** (`eclipse-temurin:21-jre`, только JRE):

1. Пользователь `spring-user` — контейнер работает **не от root**.
2. `mkdir /app/ssl` + `chown` — сюда при старте ляжет keystore.
3. `COPY --chown --chmod docker/entrypoint.sh`.
4. Четыре `COPY --from=build` — слои распакованного jar: `dependencies/` →
   `lib/` (124 библиотеки, 70 МБ), `application/` → `app.jar` (264 КБ, свой
   код и манифест с `Class-Path`), пустые `snapshot-dependencies/` и
   `spring-boot-loader/`. Слои сборочной стадии целиком в образ не
   переносятся, `target/` в runtime-образе отсутствует.
   Права задаются здесь же: отдельный `RUN chown -R /app` после `COPY` дал бы
   слой размером с содержимое jar (≈73 МБ) только ради смены владельца,
   поэтому его нет.
5. `ENV JAVA_*` — флаги JVM (память, GC-лог, JFR, JMX, heap dump при OOM).
   Это конфигурация JVM, она живёт в образе и переопределяется
   переменными окружения. Часть из них пишет в `/tmp` — см. §7.1,
   диагностика по умолчанию обнуляется в compose — §7.8.
6. `EXPOSE 8443`, `ENTRYPOINT ["/app/entrypoint.sh"]` — запуск
   `java -jar app.jar`: манифест сам поднимает `lib/`.

### 2.2. Чего в образе нет

| | Почему |
|---|---|
| `.env` и все 4 секрета | отсекается `.dockerignore` ещё до отправки контекста в daemon, а в Dockerfile не упоминается |
| `keystore.p12` и `KEY_STORE_PASS` | генерируется при старте контейнера, см. §4.3 |
| Значения конфигурации | они в `docker-compose.yaml` и `docker/app.env`, а не в jar. В jar остаётся только то, что живёт внутри кода: уровни логинга профилей, тексты писем, сообщения об ошибках — см. §3.2 |
| Исходники, `target/`, тесты | только в стадии `build`, в runtime не переносятся |

**Линт Dockerfile.** Основная проверка — встроенный чек BuildKit. Он
находит предупреждения уровня Dockerfile (в том числе
`SecretsUsedInArgOrEnv`) и **возвращает ненулевой код при находке**, то есть
годится как шаг CI:

```bash
docker build --check .          # exit 0 — чисто, exit 1 — есть замечания
```

`SecretsUsedInArgOrEnv` срабатывает по **имени** переменной: целым
фрагментом (по подчёркиванию) в ней должны встретиться `KEY`, `SECRET`,
`TOKEN` или `PASSWORD`. Значение не проверяется:

| Инструкция | Предупреждение |
|---|---|
| `ARG KEY_STORE_PASS=x` | есть |
| `ARG MY_KEY=x` | есть |
| `ARG KEYSTONE=x` | нет (не целое слово) |
| `ARG FOO=secretpw` | нет (чувствительное слово только в значении) |

В `Dockerfile` таких имён нет, поэтому `docker build --check .` проходит
чисто.

**Проверки образа.** Скрипт закоммичен как
[`docker/check-image.sh`](docker/check-image.sh) и приведён ниже целиком.
Он прогоняет все проверки и завершается ненулевым кодом при любой находке,
то есть годится как шаг CI:

```bash
#!/usr/bin/env bash
# Проверки собранного образа. Ненулевой код возврата = найдено нарушение.
# Вызов: docker/check-image.sh [образ]   (по умолчанию realhelpdesk:latest)
set -u
IMG="${1:-realhelpdesk:latest}"
fail=0

report() {                     # $1 = находки (пусто = ок), $2 = описание
  if [ -n "$1" ]; then
    printf 'FAIL %s\n%s\n' "$2" "$1"
    fail=1
  else
    printf 'OK   %s\n' "$2"
  fi
}

# 1. Имена секретов не встречаются ни в одной инструкции сборки.
#    (Значения build-arg'ов BuildKit раскрывает в истории: там видно
#     и "ARG SECRET=...", и "RUN |1 SECRET=... keytool ...".)
out=$(docker history --no-trunc "$IMG" \
      | grep -iE 'JWT_SECRET|DB_PASSWORD|KEY_STORE_PASS|MAIL_PASSWORD|storepass' || true)
report "$out" "docker history без имён секретов"

# 2. Переменные окружения образа — только конфиг, без секретов.
out=$(docker inspect "$IMG" --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | grep -iE 'SECRET|PASSWORD' || true)
report "$out" "Config.Env без секретов"

# 3. Файловая система контейнера: нет ни .env, ни keystore.
#    --entrypoint sh отключает entrypoint, то есть keytool не успел
#    сработать и мы смотрим образ как есть. 2>/dev/null обязателен:
#    контейнер работает не от root, и find иначе печатает Permission denied.
out=$(docker run --rm --entrypoint sh "$IMG" \
      -c 'find / -xdev \( -name .env -o -name "*.p12" \) -not -path "/proc/*" 2>/dev/null' || true)
report "$out" "ФС контейнера без .env и *.p12"

# 4. Ключевой материал не запечён внутрь jar. find из п. 3 архив не открывает,
#    поэтому jar проверяется отдельно — на хосте (unzip/jar в runtime-образе нет).
for jar in target/*.jar; do
  [ -e "$jar" ] || continue
  out=$(unzip -l "$jar" 2>/dev/null | grep -iE '\.(p12|pem|key|jks)$' || true)
  report "$out" "$jar без ключевого материала"
done

# 5. Если установлен trivy — скан на секреты.
if command -v trivy >/dev/null 2>&1; then
  trivy image --scanners secret "$IMG" || fail=1
else
  printf 'SKIP trivy (не установлен: https://trivy.dev/latest/getting-started/installation/)\n'
fi

exit "$fail"
```

Почему проверяются именно эти места: имя сборки не говорит ничего о
содержимом образа, `docker inspect` показывает только ENV, `find` не умеет
заглядывать в архивы, а trivy покрывает остаточные случаи. Ни одна из
проверок не заменяет остальные.

### 2.3. Контекст сборки и `.dockerignore`

`Dockerfile` копирует только `pom.xml`, `src/`, `config/` и `docker/`, но
daemon получает **всю рабочую директорию** целиком. Без `.dockerignore` в
контекст вместе с кодом уходят `.env`, `target/`, `.git/` и `*.p12` — файлы
не попадают в слои образа, но уезжают по сети на демон и оседают в его
кэше.

**Паттерны в `.dockerignore` сопоставляются только с корнем контекста**, в
отличие от `.gitignore`, где `*.p12` сработал бы в любой папке. Поэтому
ключевые и «секретные» шаблоны обязаны начинаться с `**/`:

| Написано | Сработает для |
|---|---|
| `*.p12` | только `./keystore.p12` в корне |
| `**/*.p12` | любой `*.p12` на любой глубине, включая `src/main/resources/keystore.p12` |

Это не косметика: файл `src/main/resources/keystore.p12` при паттерне
`*.p12` попал бы в контекст, прошёл бы через `COPY src` и оказался **внутри
jar**, и команда `find /` из §2.2 его бы не нашла — она архивы не открывает.

`.dockerignore` отсекает от контекста: `**/.env`, `**/.env.*`, `**/*.p12`,
`**/*.key`, `**/*.pem`, `**/*.jks`, `**/ssl/`, `target/`, `bin/`, `build/`,
`.gradle/`, `.idea/`, `*.iml`, `.git/`, `docs/`, `scripts/`,
`postman_collection.json`, `**/*.md`, `**/*.log`.

Проверка: `docker build` не должен ругаться на отсутствующие
`src`/`pom.xml`/`docker`, а прямой `COPY` отсечённого файла — должен падать
с `not found`:

```bash
# падает — файла нет в контексте: так и должно быть
printf 'FROM alpine\nCOPY src/main/resources/keystore.p12 /x\n' > /tmp/Dockerfile
docker build -f /tmp/Dockerfile .      # -> "not found"
```

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
.env  ───────────┐
                 │
docker-compose.yaml ──► docker compose ──► environment контейнера
                 │      (интерполяция $)          │
docker/app.env ──┘  env_file                       ▼
      приоритет: environment                JVM: переменная окружения
      перекрывает env_file                          │
                                                    ▼
                      application.properties:  spring.mail.host=${MAIL_HOST}
                                                    │
                                                    ▼
                      Spring резолвит ${...}  ─►  свойство конфигурации
```

Файлы схемы (`application.properties`, `application-domains.properties`)
**не хранят значений** — только ссылки.
Spring резолвит их из окружения контейнера. Если переменной нет,
резолв бросает `Could not resolve placeholder 'MAIL_HOST'` и приложение
не стартует. Тихих подстановок нет.

`docker/app.env` compose читает до запуска контейнера и передаёт значения
как обычные переменные окружения, поэтому механизм разрешения не меняется:
`environment` перекрывает `env_file`, а правило про `$` из §4.1 действует
на этот файл так же, как на `.env`.

### 3.2. Где что править

| Что меняется | Файл | Команда |
|---|---|---|
| Порог рейт-лимита | `docker/app.env` → `RATE_LIMIT_*` | `docker compose up -d` |
| Капча вкл/выкл | `docker/app.env` → `CAPTCHA_ENABLED` | `docker compose up -d` |
| Срок жизни токена, `SameSite` cookie | `docker/app.env` → `JWT_*_TOKEN_EXPIRATION`, `JWT_COOKIE_SAMESITE` | `docker compose up -d` |
| `X-Forwarded-For` | `docker-compose.yaml` → `TRUST_PROXY_HEADERS` | `docker compose up -d` |
| Профиль логов | `docker-compose.yaml` → `SPRING_PROFILES_ACTIVE` | `docker compose up -d` |
| Почта: SMTP-сервер, хост, порт | `docker-compose.yaml` | `docker compose up -d` |
| Почта: адреса отправителя, название, домен фронта | `docker/app.env` (`FRONT_DOMAIN` — в compose: зависит от окружения) | `docker compose up -d` |
| Домены: CORS, issuer/audience токенов, ссылки в письмах | схема — `application-domains.properties`, значения — `docker-compose.yaml` (`CORS_ALLOWED_ORIGINS`, `JWT_ISSUER`, `JWT_AUDIENCE`, `FRONT_DOMAIN`) и `docker/app.env` (`MAIL_FROM`, `MAIL_NOTIFY`) | `docker compose up -d` |
| Любое значение вне `.env` | `docker/app.env` (продукт) или `docker-compose.yaml` (окружение) | `docker compose up -d` |
| Любой секрет | `.env` | `docker compose up -d` |
| Новый ключ конфига | сначала `application.properties` (домены — `application-domains.properties`), потом `docker/app.env` или `docker-compose.yaml` (нельзя в обоих) | `mvn test` (сверка) |
| Тексты и темы писем | `src/main/resources/messages.properties` | пересборка (файл внутри jar) |
| Уровни логирования | `application-debug.properties` / `application-prod.properties` | пересборка |
| Флаги JVM: диагностика | `docker-compose.yaml` (выкл.) / `docker-compose.debug.yaml` (вкл.) | `docker compose up -d` |
| Флаги JVM: память | `docker-compose.yaml` → `JAVA_XMX`, `JAVA_RAM_PERCENTAGE` | `docker compose up -d` |
| Прочие флаги JVM (GC-лог, ошибки) | `Dockerfile` → `ENV JAVA_*` | пересборка |

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
| `DB_PASSWORD` | пароль PostgreSQL, общий для приложения и БД | из `.env` в compose → `DB_PASS` → `spring.datasource.password`; в сервисе `postgres` → `POSTGRES_PASSWORD` | запрещено |
| `KEY_STORE_PASS` | пароль keystore с приватным ключом TLS | `server.ssl.key-store-password`, генерация keystore | запрещено |
| `MAIL_PASSWORD` | пароль SMTP | `spring.mail.password` | **допустимо** — smtp4dev работает без аутентификации |

Имена у переменной из `.env` и у переменной контейнера различаются: в
`.env` это `DB_PASSWORD`, compose подставляет его в контейнер как `DB_PASS`,
и именно `DB_PASS` стоит в схеме `${DB_PASS}`. Запоминать это нужно при
любой миграции на файловые секреты (§4.5).

Шаблон — `.env.example`. Рабочий `.env` в git не попадает (`.gitignore`),
права на файл лучше держать `600`.

> **Предупреждение про `$`.** Compose интерполирует `$` в значениях из
> `.env` и из `docker/app.env`: пароль `pa$sword` станет `pa` (переменная
> `sword` не определена), и значение молча испортится. Экранируйте как
> `p$$ss`. Одинарные и двойные кавычки от интерполяции **не** спасают —
> проверено на Docker Compose v5.5.1. В `docker/app.env` при этом `#`
> в середине значения тоже не работает: всё после `#` отбрасывается как
> комментарий.

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

Правило про `$` из §4.1 относится и сюда: значение, пришедшее из `.env`,
интерполируется до того, как попадёт в `environment`.

### 4.3. Keystore и его пароль

Keystore **не создаётся при сборке**: пароль не должен попадать в слои
образа и в `docker history`.

> **Почему так.** Пока `KEY_STORE_PASS` был build-arg, пароль попадал в
> образ. Build-arg доступен как переменная окружения внутри `RUN` и
> **записывается в историю образа** — `docker history --no-trunc` показывает
> и `ARG SECRET=...`, и `RUN |1 SECRET=... keytool ...` со значениями.
> BuildKit дополнительно предупреждает строкой
> `SecretsUsedInArgOrEnv: Do not use ARG or ENV instructions for sensitive data`
> (срабатывает по имени переменной: `KEY_STORE_PASS` содержит `KEY`).

Генерация выполняется в `docker/entrypoint.sh` при старте контейнера:

1. Если `/app/ssl/keystore.p12` отсутствует — создаёт самоподписанный
   сертификат через `keytool`, пароль берётся из окружения.
2. Если файл **смонтирован извне** (прод: свой сертификат) — генерация
   пропускается, используется монтированный файл.
3. Без `KEY_STORE_PASS` **или `KEY_STORE_ALIAS`** контейнер падает с внятной
   ошибкой (`set -eu` и `:?`) — обе переменные обязаны быть в окружении.
4. Дальше `exec java ...` — флаги JVM передаются без изменений.

**Пароль не должен попадать в список процессов.** keytool принимает его
через переменную окружения, а не аргументом командной строки:

```bash
keytool -genkeypair ... \
    -storepass:env KEY_STORE_PASS \
    -keypass:env KEY_STORE_PASS
```

При `-storepass` / `-keypass` значение читается из `/proc/<pid>/cmdline` и
видно любому, у кого есть `docker exec` или `ps` в контейнере. Вариант
`:env` проверен на образе проекта — keystore создаётся, пароля в cmdline
нет.

Два следствия, которые легко пропустить:

- **Смонтированный keystore несёт собственный пароль.** Значение
  `KEY_STORE_PASS` должно быть равно паролю самого файла: Tomcat сверяет их
  при открытии. Несовпадение валит старт — ошибка от Tomcat, а не от
  entrypoint, потому что генерация в этом случае пропускается.
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

# пароль PostgreSQL: сначала ALTER ROLE в БД, потом .env → DB_PASSWORD
docker compose up -d

# пароль SMTP
docker compose up -d

# keystore: новый пароль в .env → KEY_STORE_PASS
docker compose up -d
```

Ни одна ротация не требует пересборки образа, но у каждой есть цена:

- **`JWT_SECRET` инвалидирует все выданные токены.** После пересоздания
  контейнера каждый клиент получит 401 и перелогинится. Это массовый
  разлогин, а не «бесшовное обновление». **Обходной путь** — выдавать новый
  ключ с `kid` и держать старый в наборе на время жизни токенов; сейчас
  этого нет, поэтому планируйте ротацию на «тихое» окно.
- **`DB_PASSWORD`: порядок обязателен.** `POSTGRES_PASSWORD` применяется
  только при **первой** инициализации тома. На существующем `postgres_data`
  он ни на что не влияет, поэтому менять пароль можно только через
  `ALTER ROLE <user> WITH PASSWORD '...'` в самой БД, и только потом
  обновлять `.env`. В обратном порядке приложение упадёт на первом же
  запросе.

  Уже открытые соединения после смены пароля продолжают работать —
  PostgreSQL не перепроверяет пароль на живом соединении. Новые соединения
  будут отклоняться до перезапуска `app`, то есть после `ALTER ROLE` нужно
  ещё сделать `docker compose up -d`, чтобы пул переподключился.
- **`KEY_STORE_PASS`:** см. §4.3 — для смонтированного keystore значение в
  `.env` должно совпадать с паролем самого файла, а не просто быть новым.

### 4.5. Ограничение, о котором надо знать

Переменные окружения видны любому, у кого есть доступ к Docker:

```bash
docker compose up -d                                # если контейнера ещё нет
docker inspect realhelpdesk --format '{{range .Config.Env}}{{println .}}{{end}}'
```

`docker inspect` работает и с остановленным контейнером — ему нужен только
созданный контейнер. Ошибка `No such object` означает, что контейнера нет
вообще: он либо никогда не создавался, либо удалён.

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
Поэтому имя файла обязано **точно совпадать** с плейсхолдером в
`application.properties`: там лежит `${DB_PASS}`, значит файл должен
называться `DB_PASS`, а не `DB_PASSWORD` из `.env`.

Как это выглядело бы:

```yaml
services:
  app:
    secrets: [DB_PASS, JWT_SECRET, KEY_STORE_PASS]   # вместо этих ключей в environment
    environment:
      SPRING_CONFIG_IMPORT: "optional:configtree:/run/secrets/"
      MAIL_PASSWORD: "${MAIL_PASSWORD-}"             # остаётся, ей нужен пустой дефолт

secrets:
  # имя секрета = имя файла в /run/secrets = имя в схеме;
  # источник — переменная окружения хоста (та, что сейчас в .env)
  DB_PASS:        { environment: DB_PASSWORD }
  JWT_SECRET:     { environment: JWT_SECRET }
  KEY_STORE_PASS: { environment: KEY_STORE_PASS }
```

Что это меняет и что придётся поправить:

- секреты перестают попадать в `docker inspect ... Config.Env`, но в
  обычном `docker compose` (не Swarm) они всё равно пишутся во временные
  файлы на хосте и монтируются в контейнер — на хосте они никуда не деваются;
- **`docker/entrypoint.sh` перестанет работать.** Скрипт читает
  `KEY_STORE_PASS` из окружения и проверяет его через `:?`. При переводе
  переменной в `secrets:` в окружении её не окажется, и контейнер упадёт на
  первом же шаге. Нужно менять скрипт на чтение
  `/run/secrets/KEY_STORE_PASS` (например,
  `KEY_STORE_PASS="$(</run/secrets/KEY_STORE_PASS)"` до проверки);
- `SPRING_CONFIG_IMPORT` добавит в `services.app.environment` лишний ключ,
  которого нет в схеме, — тест `ApplicationConfigSchemaTest` (сверка
  «схема ↔ environment») упадёт. Список исключений в тесте уже есть (ключи
  `JAVA_*`, §7.8), останется добавить туда `SPRING_CONFIG_IMPORT`;
- `DB_PASSWORD` нужен и самой PostgreSQL — этот ключ остаётся
  в `environment` сервиса `postgres`, как и сейчас.

Цепочка прогнана на стенде: compose с `secrets:` и
`SPRING_CONFIG_IMPORT: optional:configtree:/run/secrets/` стартует —
`${DB_PASS}` и `${JWT_SECRET}` берутся из файлов `/run/secrets/*` (имена
файлов совпадают с плейсхолдерами), Hibernate поднимает схему, приложение
отвечает на `/api/v1/health`, ошибок резолва нет. Тот же запуск **без**
правки entrypoint падает на первом же шаге с
`KEY_STORE_PASS: KEY_STORE_PASS is required` — окружения для скрипта
не существует, есть только файл.

Поэтому переход — не «всего лишь замена секции», а правка compose, скрипта
запуска и теста. Ровно по этой причине переход не сделан.

### 4.6. Что запрещено

- секрет в git, в `Dockerfile`, в `docker-compose.yaml` **литералом**
  (только `${VAR:?}` из `.env`);
- секрет в логах, в исключениях, в URL запроса;
- `ARG`/`ENV` со значением секрета в `Dockerfile`;
- дефолт секрета (`${JWT_SECRET:-какой-то-текст}`) — это скрытый пароль
  в репозитории;
- `*.p12`, `*.key`, `*.pem`, `ssl/` в git — отсекает `.gitignore`;
- `.env`, `*.p12` и т. п. в контексте сборки — отсекает `.dockerignore`
  (паттернами с префиксом `**/`, см. §2.3).

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

Публикация `127.0.0.1:5432` **необязательна**: она нужна, только если к БД
должен подключаться хост (pgAdmin, DBeaver, отладка). Если PostgreSQL нужен
исключительно сервису `app`, строку `ports:` у `postgres` можно удалить
целиком — внутри compose-сети он доступен по имени `postgres` и без неё.

Готовность: у `postgres` есть `healthcheck` (`pg_isready`), и `app` ждёт
`condition: service_healthy`, а не просто «контейнер запущен». Без этого
Hibernate стартует до готовности СУБД и падает, а `restart: on-failure:2`
даёт приложению только две попытки.

Политика перезапуска у всех сервисов — `on-failure:2`: две попытки, дальше
контейнер остаётся остановленным. См. §7.4.

---

## 6. Чек-лист при ревью

- [ ] Новое свойство в `application.properties` (домены — в
      `application-domains.properties`) — ровно `${VAR}`, без дефолта.
- [ ] Для него добавлен ключ в `services.app.environment` **или** в
      `docker/app.env` — в обоих сразу тест упадёт на дубле (`mvn test`
      проверит и парность, и дубли). Ключи compose, начинающиеся с `JAVA_`,
      в схему не входят — это переопределения `ENV` образа.
- [ ] Ключ попал в слой по смыслу (§1): продукт (лимиты, капча, сроки,
      адреса писем) — в `docker/app.env`, окружение (сеть, порты, профиль,
      ресурсы) — в `docker-compose.yaml`.
- [ ] Значение — литерал, не `${VAR:-дефолт}` — и в compose, и в `app.env`.
- [ ] Значение вне секретов не содержит неэкранированного `$` (§4.1) —
      это касается и `docker/app.env`.
- [ ] Секрет — только в `.env`, в compose подставлен через `${VAR:?}`.
      Исключение — `MAIL_PASSWORD`: ему нужен пустой дефолт
      `${MAIL_PASSWORD-}` (§4.2), поэтому он остаётся единственной
      переменной с дефолтом.
- [ ] Значение секрета не содержит неэкранированного `$` (§4.1).
- [ ] Ничего нового в `Dockerfile` вида `ARG`/`ENV` с чувствительным именем
      (содержит целым фрагментом `SECRET`, `PASSWORD`, `TOKEN`, `KEY`) —
      такие подсветит `docker build --check .` предупреждением
      `SecretsUsedInArgOrEnv`, а значение осядет в `docker history`.
- [ ] Новые типы файлов (ключи, `.env`, логи) отражены в `.dockerignore`
      паттернами с префиксом `**/` (§2.3).
- [ ] Диагностика JVM по умолчанию выключена: `JAVA_JFR_OPTS`,
      `JAVA_HEAP_DUMP_OPTS`, `JAVA_NMT_OPTS`, `JAVA_JMX_OPTS` в
      `docker-compose.yaml` пусты, включается она только
      `docker-compose.debug.yaml` (§7.8).
- [ ] Укрепление контейнера на месте: `read_only`, `tmpfs`, `cap_drop`,
      `security_opt`, `mem_limit` (§7.9).
- [ ] Образ чист: `docker build --check .` выходит с кодом 0,
      `docker/check-image.sh` выходит с кодом 0,
      `trivy image --scanners secret` без находок (если trivy установлен).
- [ ] Изменение сконфигурировано без пересборки (или в README явно
      сказано, почему нужна пересборка).

---

## 7. Что явно не решено

Ограничения текущей схемы. **§7.6, §7.8 и §7.9 уже подключены** — остались
только замечания о том, как они работают. Остальное некритично для
доверенного одиночного хоста, но **§7.2 (JMX) — исключение: при запуске
мимо compose его нужно закрыть до прода**.

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

Сделано: диагностика по умолчанию погашена (§7.8), а `/tmp` выведен в
`tmpfs` (`read_only: true` + `tmpfs`, §7.9) — файлы живут в оперативной
памяти и исчезают вместе с контейнером. Остаётся GC-лог: он пишется в
`/tmp` всегда и в compose, поэтому при `read_only` его место именно там.

### 7.2. JMX без аутентификации — критично

В образе включены `jmxremote.authenticate=false`, `jmxremote.ssl=false`,
порт 5005. Это переменная `ENV` в `Dockerfile`, а не значение профиля, то
есть сам по себе образ отдаёт JMX всем профилям — профиль `prod` его не
выключает. В compose-запуске `JAVA_JMX_OPTS` обнуляется (§7.8), а проблема
ниже остаётся для запуска мимо compose.

Порта нет ни в `EXPOSE`, ни в `ports:` — с хоста он недоступен. Внутри
контейнера сокет слушает на **всех** интерфейсах (проверено:
`/proc/net/tcp6` → `:::138D`, состояние LISTEN), поэтому доступен любому
контейнеру той же Docker-сети.

`java.rmi.server.hostname=127.0.0.1` защитой **не является**: атакующий в
той же сети пробрасывает порт на свой localhost (`socat TCP-LISTEN:15005,fork
TCP:app:5005` или `ssh -L 15005:app:5005`), и stub, указывающий на
`127.0.0.1`, начинает работать в его пользу.

Неаутентифицированный JMX даёт **удалённое выполнение кода** — через
MBean-сервис `MLet` достаточно загрузить свой MBean. Это не «доступ к
метрикам», а полный контроль над JVM.

Исправление, в порядке убывания предпочтительности:

1. Отключить JMX по умолчанию (`JAVA_JMX_OPTS=""`) и включать его только в
   debug-override compose — см. §7.8.
2. Если JMX обязателен — привязать его к loopback либо включить
   `-Dcom.sun.management.jmxremote.local.only=true`.
3. Включить аутентификацию (`jmxremote.authenticate=true` с файлом
   паролей) и SSL.

Для compose-запуска уже сделано первое: `docker-compose.yaml` обнуляет
`JAVA_JMX_OPTS`, и без `docker-compose.debug.yaml` порта 5005 в контейнере
нет (проверено по `/proc/net/tcp6`). Остаток — образ, запущенный мимо
compose: он берёт `ENV` из `Dockerfile`, где JMX включён (§7.8).

### 7.3. Нет healthcheck у `app`

`postgres` проверяется через `pg_isready`, у `app` healthcheck не описан —
`docker compose ps` и внешние мониторы не отличат живой процесс от зависшего.

HTTP-эндпоинт для проверки при этом уже есть: `GET /api/v1/health`
(`HealthCheckController`), он входит в `WhiteUrlConfig`, то есть не требует
аутентификации, и отвечает `{"status":"UP","timestamp":...}`.

В `eclipse-temurin:21-jre` есть `curl`, `wget` и `bash` (проверено на текущем
теге), `python3`, `busybox` и `nc` — нет. Полагаться на `curl` не стоит:
теги не прибиты к digest (§7.5), и при смене апстрима утилита может
исчезнуть, поэтому минимальная проверка должна обходиться одним `bash`:

```yaml
healthcheck:
  # JVM отвечает на порт: отличаем упавший процесс от живого
  test: ["CMD-SHELL", "timeout 3 bash -c '</dev/tcp/127.0.0.1/8443'"]
  interval: 15s
  timeout: 5s
  retries: 3
  start_period: 60s
```

Проверка уровня приложения делается тем же `curl`, но по HTTPS и с `-k` —
сертификат самоподписанный, а HTTP на 8443 нет, приложение работает только
по HTTPS:

```yaml
  test: ["CMD-SHELL", "curl -sfk --max-time 5 https://localhost:8443/api/v1/health >/dev/null"]
```

Ограничение обоих вариантов: `/api/v1/health` возвращает `UP` всегда. Это
проверка того, что контекст поднялся и Tomcat отвечает, а не проверка
зависимостей — БД, SMTP и пул соединений эндпоинт не трогает. Если нужна
проверка зависимостей, потребуется `spring-boot-starter-actuator` и
`/actuator/health` с индикатором `db`: в `pom.xml` Actuator сейчас
отсутствует, и подключение — отдельное решение, а не настройка healthcheck.

### 7.4. `restart: on-failure:2`

Две попытки, дальше контейнер лежит остановленным. Это безопаснее, чем
раньше, потому что гонка «app стартует раньше postgres» устранена условием
`service_healthy`. Но если нужна самовосстановляемость — менять на
`unless-stopped`.

### 7.5. Образы по тегу, не по digest

`maven:3.9.6-eclipse-temurin-21`, `eclipse-temurin:21-jre`, `postgres:15`,
`rnwood/smtp4dev` — теги могут «уехать» вместе с апстримом. Для прода
нужен pin по digest + Renovate/Dependabot.

### 7.6. Сборка: кэш Maven и слои jar

Два места, где сборка тратит время. Это вопрос **скорости сборки**, не
безопасности. Оба уже подключены.

**Кэш Maven.** Сборка идёт с `RUN --mount=type=cache,target=/root/.m2`:
зависимости живут в кэше BuildKit, а не в слое образа. Слой с
`mvn package` инвалидируется при любой правке `src/`, но правка `pom.xml`
кэш зависимостей не сбрасывает, поэтому отдельный
`mvn dependency:go-offline` не нужен.

**Слои jar.** `java -Djarmode=tools -jar app.jar extract --layers` раскладывает
jar на слои, и в образе это даёт две разные сущности: `app.jar` (264 КБ —
свой код и манифест с `Class-Path`) и `lib/` из 124 библиотек (70 МБ).
Правка кода пересобирает только слой `application/`. Проверяется по логу
сборки: после правки в `src/` шаг `COPY .../dependencies/` — `CACHED`,
`COPY .../application/` — пересобран, а `mvn package` не ходит в Maven
Central.

Запуск при этом не изменился: `java -jar app.jar`, манифест сам поднимает
`lib/` — никакого отдельного шага распаковки при старте контейнера нет.

### 7.7. Терминация TLS в приложении

Сейчас HTTPS поднимает сам Tomcat. Прод-альтернативы: Spring SSL bundles
(PEM-файлы, без keytool) или терминация на reverse proxy с отключением
HTTPS в приложении.

### 7.8. Безопасные значения по умолчанию

Диагностика (JMX, JFR, heap dump при OOM, NMT) объявлена как `ENV` в
`Dockerfile`, то есть одинакова для всех профилей — профиль `prod` её не
выключает, он меняет только уровни логирования (§3.3). По умолчанию её гасит
`docker-compose.yaml`: переменные окружения контейнера перекрывают `ENV`
образа.

```yaml
# docker-compose.yaml — по умолчанию диагностика выключена
services:
  app:
    environment:
      JAVA_JFR_OPTS: ""
      JAVA_HEAP_DUMP_OPTS: ""
      JAVA_NMT_OPTS: ""
      JAVA_JMX_OPTS: ""
```

Включение — явное решение на конкретном окружении, отдельным файлом
`docker-compose.debug.yaml`, который подключается вторым `-f`:

```bash
docker compose -f docker-compose.yaml -f docker-compose.debug.yaml up -d
```

```yaml
# docker-compose.debug.yaml
services:
  app:
    environment:
      JAVA_JFR_OPTS: "-XX:StartFlightRecording=disk=true,dumponexit=true,filename=/tmp/,maxsize=1g,maxage=24h"
      JAVA_HEAP_DUMP_OPTS: "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
      JAVA_NMT_OPTS: "-XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics"
      JAVA_JMX_OPTS: "-Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=5005 -Dcom.sun.management.jmxremote.rmi.port=5005"
```

Проверено на образе: без override порта 5005 и файлов JFR в контейнере нет,
с ним порт слушается, запись JFR появляется в `/tmp`, `/api/v1/health`
отвечает. Пустое значение безопасно для entrypoint — `${JAVA_JMX_OPTS}` с
`set -eu` разворачивается в ноль слов и просто отбрасывается.

Так закрывается большая часть §7.1 и §7.2. Остаток: образ, запущенный мимо
compose (`docker run`), берёт `ENV` из `Dockerfile` и диагностику всё ещё
получает — если такой запуск планируется, пустые значения стоит перенести и
в `Dockerfile`.

### 7.9. Укрепление контейнера

Базовые жёсткие настройки подключены в `docker-compose.yaml`:

```yaml
services:
  app:
    read_only: true            # корень ФС контейнера только для чтения
    tmpfs:
      - /tmp                   # GC-лог, JFR, heap dump, java_error.log
      - /app/ssl:rw,uid=999,gid=999   # keystore; 999 — spring-user
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    mem_limit: 768m
    memswap_limit: 768m
    environment:
      JAVA_XMX: ""             # пусто → heap считается как доля от mem_limit
      JAVA_RAM_PERCENTAGE: "45" # 45% от 768m ≈ 345M
```

Проверено на образе проекта: при `read_only: true` с такими `tmpfs` keystore
создаётся (`/app/ssl` монтируется владельцем `spring-user:spring-group`),
запись в `/app` отклоняется с `Read-only file system`, приложение стартует и
отвечает на `/api/v1/health`, потребление — около 300–345 МБ из 768-ми.
Права `uid=999,gid=999` нужны именно потому, что иначе tmpfs достанется root,
а контейнер работает от `spring-user`.

`cap_drop: [ALL]` и `no-new-privileges` не конфликтуют с запуском:
приложению нужны только стандартные сетевые вызовы, а `keytool` и `java`
привилегий не требуют.

**Память.** В `entrypoint.sh` выбор между жёстким `-Xmx` и долей:

- `JAVA_XMX` задан → используется `-Xmx${JAVA_XMX}`, `MaxRAMPercentage`
  игнорируется (проверено: при `-Xmx64m -XX:MaxRAMPercentage=75` JVM берёт
  64 МБ, source `{command line}`);
- `JAVA_XMX` пуст → используется `-XX:MaxRAMPercentage=${JAVA_RAM_PERCENTAGE}`,
  и heap считается от **лимита контейнера**: 45% от 768m дают
  `MaxHeapSize = 362807296` (≈346 МБ), то есть тот же heap, что и раньше.

Доля без `mem_limit` опасна: та же команда на контейнере без ограничения дала
15 ГБ (доля считается от памяти хоста). Поэтому `JAVA_RAM_PERCENTAGE` имеет
смысл только вместе с `mem_limit` — эти две настройки меняются парой.

`read_only: true` без `tmpfs` для `/app/ssl` ломает генерацию keystore —
entrypoint не сможет записать файл (§4.3), а `tmpfs: /tmp` со своей стороны
закрывает §7.1: heap dump, JFR, GC-лог и `java_error.log` уходят в
оперативную память и исчезают вместе с контейнером.
