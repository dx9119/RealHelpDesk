# План внедрения наблюдаемости в RealHelpDesk

Что видно по POM: Spring Boot 4.1.1 (модульные стартеры), Java 21, JPA + PostgreSQL + Liquibase, шарды БД (`make migrate-shard`), Security + OAuth2 resource server + JWT, MinIO, Mail, Kaptcha. Actuator и Prometheus уже есть, трейсинга и логов с контекстом нет.

Промпты идут по порядку, каждый можно давать агенту отдельно. Для Boot 4 несколько имён артефактов и свойств изменились, поэтому в промптах стоит требование сверяться с документацией, а не писать по памяти.

---

## Шаг 0. Контекст 

```text
Мы внедряем наблюдаемость в Spring Boot 4.1.1 проект (Java 21, Maven, Spring MVC, Spring Data JPA/Hibernate,
PostgreSQL с шардами, Liquibase, Spring Security + OAuth2 Resource Server + JWT (jjwt), MinIO, Spring Mail).
Уже подключены spring-boot-starter-actuator и micrometer-registry-prometheus.

Правила работы на всех шагах:
1. Boot 4 модульный: названия стартеров и свойств могли измениться относительно Boot 3. Перед добавлением
   зависимости или свойства проверяй актуальную документацию Spring Boot 4.1 / Micrometer / OpenTelemetry
   (web_fetch/поиск), не пиши по памяти. Если не уверен в имени артефакта, скажи об этом явно.
2. Единая точка инструментирования: Micrometer Observation API (одно наблюдение = метрика + спан).
   Не дублируй Timer и ручные спаны.
3. Кардинальность: в low-cardinality теги только ограниченные значения. userId, ticketId, email, полный URL,
   текст исключения запрещены в метриках (допустимы только как highCardinalityKeyValue в трейсах, и то не ПДн).
4. Никаких ПДн, токенов, паролей, JWT, содержимого писем и вложений в логах, тегах и спанах.
5. Проект проверяется Spotless и Checkstyle в фазе validate (4 пробела, длина строки 140, порядок импортов
   java,javax,jakarta,org,com,static). После изменений запускай `mvn validate` и `mvn test`.
6. Делай минимальные атомарные изменения, не рефакторь несвязанный код. Весь новый код покрывай тестами.
7. В конце каждого шага выдай: список изменённых файлов, как проверить вручную, что осталось нерешённым.

Подтверди, что понял правила, и ничего не меняй до следующей команды.
```

---

## Шаг 1. Аудит текущего состояния

```text
Проведи аудит проекта БЕЗ изменений кода и выдай отчёт:
1. Что уже настроено: application*.yml/properties (management.*, logging.*), SecurityConfig
   (WHITE_LIST_URLS, правила для /actuator/**), наличие Dockerfile/docker-compose/Makefile/k8s-манифестов.
2. Как устроена работа с шардами БД: где создаются DataSource, как выбирается шард, как именуются пулы
   HikariCP, есть ли AbstractRoutingDataSource.
3. Список внешних интеграций и мест их вызова: MinIO (вложения), SMTP (отправка писем), Kaptcha, любые
   RestClient/RestTemplate/WebClient. Для каждого укажи класс и метод.
4. Использование @Async, @Scheduled, CompletableFuture, ExecutorService, @EventListener, @TransactionalEventListener.
5. Ключевые бизнес-сценарии (создание заявки, смена статуса, назначение, комментарии, вложения, логин/регистрация,
   сброс пароля) с указанием сервисных методов. Это список кандидатов для бизнес-метрик.
6. Как сейчас логируются ошибки (GlobalExceptionHandler, уровни, дублирование логов), есть ли логирование
   ПДн/токенов.
7. Есть ли в логах конфигурация (logback-spring.xml) и формат.
Формат: таблица «область, текущее состояние, проблема/риск, рекомендация». Пока ничего не меняй.
```

---

## Шаг 2. Базовая конфигурация Actuator и Micrometer

```text
Настрой базовую конфигурацию наблюдаемости в application.yml (и профильных файлах):
1. spring.application.name должен быть задан.
2. management.metrics.tags: application, env (из переменной ENV, дефолт local), instance (из HOSTNAME).
3. management.endpoints.web.exposure.include: только health, info, prometheus. Явно не отдавать env, heapdump,
   threaddump, configprops, beans, mappings.
4. management.endpoint.health: show-details: when-authorized, probes.enabled: true; группы liveness
   (только livenessState) и readiness (readinessState + db). Во liveness НЕ включать db, mail, minio.
5. Включи percentiles-histogram и SLO-бакеты для http.server.requests (100ms,300ms,1s,3s). Не используй
   клиентские percentiles.
6. management.observations.key-values для общих значений, если нужно.
7. Отключи шумные наблюдения /actuator/** через ObservationPredicate (или management.observations.http.server.requests
   .ignore-patterns, если такая настройка есть в Boot 4.1 — проверь).
8. Включи management.prometheus.metrics.export.enabled и убедись, что имена свойств актуальны для Boot 4.
Покажи итоговый yml и объясни каждую группу настроек в комментариях (на русском, коротко).
Проверь: приложение стартует, /actuator/health/liveness и /readiness отвечают, /actuator/prometheus
отдаёт метрики с тегами application/env.
```

---

## Шаг 3. Безопасность actuator (важная точка проекта)

```text
В проекте стоит Spring Security + OAuth2 Resource Server (JWT). По комментарию в pom, /actuator/health в
WHITE_LIST_URLS, а /actuator/prometheus за аутентификацией. Prometheus не умеет ходить с пользовательским JWT,
нужно нормальное решение:
1. Вынеси management на отдельный порт: management.server.port (дефолт 8081), management.server.address
   настраиваемый.
2. Сделай отдельную SecurityFilterChain для управляющего порта с @Order выше основной (используй
   EndpointRequest.toAnyEndpoint() или securityMatcher по порту; проверь рабочий способ для Boot 4 /
   Spring Security 7):
   - health, health/**, info: permitAll (для пробов оркестратора);
   - prometheus: доступ по HTTP Basic с отдельным сервисным пользователем (креды из env, не в репозитории)
     ИЛИ permitAll, если порт гарантированно закрыт сетью. Реализуй вариант Basic, сделай переключаемым свойством.
   - всё остальное: denyAll.
3. Основная цепочка не должна больше содержать правила для /actuator/**, и на основном порту actuator
   недоступен (проверь, что /actuator/prometheus на порту приложения отдаёт 404/401).
4. Убедись, что CSRF/CORS/JWT-фильтр не мешают управляющей цепочке и что JWT-фильтр не вызывается для management-запросов.
5. Напиши интеграционные тесты (MockMvc или SpringBootTest с RANDOM_PORT и management порт): health без
   аутентификации, prometheus без кредов 401, с кредами 200, env/heapdump недоступны.
Не ломай существующие security-тесты. Обнови WHITE_LIST_URLS, если нужно.
```

---

## Шаг 4. HTTP-метрики, ошибки и бизнес-контекст запросов

```text
Доработай HTTP-наблюдаемость:
1. Проверь, что http.server.requests использует шаблон URI (/api/tickets/{id}), а не фактический путь.
   Проверь это на несуществующих путях (404): они не должны плодить уникальные значения тега uri
   (должно быть NOT_FOUND). Добавь MeterFilter.maximumAllowableTags("http.server.requests", "uri", 100, deny()).
2. Реализуй GlobalExceptionHandler-совместимо: исключение, обработанное @ControllerAdvice, должно попадать
   в наблюдение запроса (через ServerHttpObservationFilter / request attribute по документации Spring Framework 7),
   чтобы тег exception и outcome были корректны.
3. Добавь кастомную ObservationConvention (или ObservationFilter) для http.server.requests, которая добавляет
   low-cardinality тег с ролью пользователя (ограниченный набор: ADMIN/AGENT/USER/ANONYMOUS), БЕЗ id и email.
   Высококардинальный userId в трейсы НЕ добавлять без явного разрешения (ПДн): вместо этого хэш/внутренний id
   только если подтверждено, что это допустимо. Спроси меня, если сомневаешься.
4. Метрики для аутентификации: счётчик auth.attempts с тегами type (login/refresh/captcha/password_reset)
   и result (success/failure/locked); без username в тегах.
5. Тесты на метрики через MeterRegistry (SimpleMeterRegistry/@AutoConfigureMetrics - проверь актуальное имя
   аннотации для Boot 4).
```

---

## Шаг 5. База данных: Hikari, шарды, JPA, Liquibase

```text
Настрой наблюдаемость слоя БД с учётом шардов:
1. HikariCP: убедись, что у каждого пула (основной и шарды) уникальный poolName, и метрики hikaricp.connections.*
   отдаются для каждого пула с тегом pool. Если DataSource шардов создаются вручную, а не автоконфигурацией,
   зарегистрируй их метрики через HikariDataSource.setMetricRegistry/MicrometerMetricsTrackerFactory или
   DataSourcePoolMetrics. Покажи, как проверить через /actuator/prometheus.
2. Добавь в readiness-группу проверку БД для основного datasource и кастомный HealthIndicator для шардов
   (по одному на шард или композитный, с тегом шарда в деталях). Недоступность одного шарда не должна
   перезапускать приложение (только readiness, не liveness).
3. Метрики по шардам: счётчик/таймер выбора шарда db.shard.routing (тег shard: ограниченное множество,
   задаётся конфигурацией), чтобы было видно перекос нагрузки.
4. Трассировка SQL: оцени варианты и выбери совместимый с Spring Boot 4.1 / Hibernate 7:
   (a) net.ttddyy datasource-micrometer-spring-boot, (b) OpenTelemetry JDBC instrumentation/обёртка DataSource,
   (c) Hibernate statistics + HibernateMetrics. Для каждого проверь совместимость с версиями в проекте.
   Если библиотека не поддерживает Boot 4, НЕ вставляй её наугад: предложи (c) плюс ручные Observation
   на уровне сервисов и опиши ограничения. Спроси меня перед добавлением новой зависимости.
5. Hibernate: включи hibernate.generate_statistics только через профиль/свойство (по умолчанию выключено),
   подключи метрики, добавь логирование медленных запросов по порогу (настраиваемый).
6. Не включай логирование параметров SQL (BasicBinder) вне профиля local.
7. Liquibase: убедись, что время миграций видно в логах старта; метрику не добавляй, если нет простого способа.
8. N+1: добавь в тестовый профиль/в dev простой механизм подсчёта запросов на HTTP-запрос (например, через
   Hibernate Statistics в тесте) и тест-регрессию для эндпоинта списка заявок.
Проверь на локальном стенде и дай инструкцию, какие метрики/спаны должны появиться.
```

---

## Шаг 6. Распределённая трассировка (OpenTelemetry)

```text
Добавь трассировку:
1. Найди в документации Spring Boot 4.1, какой стартер/зависимости нужны для OTLP-трейсинга
   (кандидат: spring-boot-starter-opentelemetry, либо micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp).
   Выбери один подход, объясни выбор, добавь зависимость в pom (версии через BOM Boot, без жёстких версий,
   если не требуется).
2. Конфиг: management.tracing.sampling.probability (local=1.0, prod=0.1, через env), OTLP endpoint из переменной
   окружения, resource attributes service.name/service.version/deployment.environment.
3. Распространение контекста (W3C traceparent): проверь, что исходящие вызовы (RestClient/RestTemplate, если есть)
   создаются через автоконфигурируемые Builder.
4. @Async/@Scheduled/CompletableFuture: настрой TaskDecorator для переноса контекста трассировки
   (ContextPropagatingTaskDecorator или актуальный аналог) и убедись, что executor'ы Spring Boot.
5. Исключи из трейсов /actuator/**, health-пробы и статические ресурсы (ObservationPredicate).
6. Ручные Observation (через ObservationRegistry, не через @Observed без нужды) на границах:
   - TicketService: create, changeStatus, assign (low: operation, outcome; high: ticketId, допустимо);
   - AttachmentService/MinIO: upload, download, delete;
   - MailService: send (low: template/type; НЕ адрес получателя).
   Имена: ticket.create, ticket.status.change, attachment.upload, mail.send.
   Если используешь @Observed, добавь ObservedAspect и нужный AOP-стартер (в Boot 4 проверь актуальное имя:
   spring-boot-starter-aop vs aspectj) и учти ограничения самовызова.
7. Тест с TestObservationRegistry / micrometer-tracing-test: проверяется наличие наблюдения, теги и отсутствие
   запрещённых ключей.
Выдай схему: какие спаны получатся у запроса POST /api/tickets (ожидаемое дерево).
```

---

## Шаг 7. Структурированные логи и корреляция

```text
Настрой логирование:
1. Включи структурированные JSON-логи (logging.structured.format.console=ecs или logstash) для профилей
   кроме local; в local оставь читаемый формат с traceId/spanId в pattern.
2. Убедись, что traceId и spanId попадают в MDC и в каждую запись; добавь application/env как поля.
3. Проверь и исправь дублирование логирования исключений (лог в сервисе + лог в handler). Правило: исключение
   логируется один раз на границе (GlobalExceptionHandler); 4xx как WARN/INFO без стектрейса, 5xx как ERROR
   со стектрейсом.
4. Маскирование: найди в коде логирование email, токенов, паролей, тел запросов, имён файлов вложений с ПДн.
   Убери или замаскируй. Добавь утилиту маскирования email/телефона и используй её, где логирование неизбежно.
5. Добавь в MDC безопасный контекст запроса (requestId, роль, но не email/токен) через фильтр с низким
   приоритетом после аутентификации; очищай MDC в finally.
6. Уровни логов по умолчанию: root INFO, org.hibernate.SQL и BasicBinder не выше INFO в prod.
7. Тест: вывод лога содержит traceId, не содержит заведомо чувствительные значения.
```

---

## Шаг 8. Бизнес-метрики

```text
Реализуй бизнес-метрики через Micrometer (инжекция MeterRegistry в сервисы или отдельный компонент
BusinessMetrics, чтобы не засорять доменную логику):
1. tickets.created (counter; теги: priority, category, ограниченные enum'ы)
2. tickets.status.transitions (counter; теги: from, to)
3. tickets.open (gauge по статусам; считать запросом к БД с кэшем 30-60 сек или через периодическое
   обновление, не по запросу scrape; слабые ссылки, без N запросов на scrape)
4. tickets.time.to.first.response и tickets.time.to.resolution (Timer/DistributionSummary с SLO-бакетами)
5. attachments.uploaded (counter) и attachments.size (DistributionSummary, bytes; теги: contentType-группа,
   не имя файла)
6. mail.sent (counter; теги: type, result)
7. auth.attempts (если не сделано на шаге 4)
Требования: все теги из конечных enum'ов, имена в dot.case, описание (description) и baseUnit у каждой метрики.
Вызовы метрик не должны влиять на транзакцию: инкремент после успешного коммита
(@TransactionalEventListener(AFTER_COMMIT) или аналог), чтобы откаты не завышали счётчики.
Покрой тестами. Составь таблицу «метрика, тип, теги, смысл» и положи в docs/observability/metrics.md.
```

---

## Шаг 9. Внешние зависимости: MinIO, SMTP, Kaptcha

```text
Добавь наблюдаемость внешних зависимостей:
1. MinIO: оберни вызовы клиента в Observation (минимум: имя операции, результат; бакет только если их
   ограниченное число). Метрика длительности и ошибок. HealthIndicator для MinIO (проверка доступности бакета
   с таймаутом 1-2 сек), включить только в readiness с возможностью отключения свойством.
2. SMTP: Observation вокруг отправки (тип письма, результат), таймауты уже заданы? Если нет, предложи.
   HealthIndicator для почты лучше НЕ включать в readiness (спроси меня), выведи в отдельную группу.
3. Любые вызовы RestClient/RestTemplate: проверь автоинструментирование (http.client.requests).
4. Для всех кастомных HealthIndicator: таймаут, кэширование результата на несколько секунд, исключение
   тяжёлых операций.
5. Тесты: подмена клиентов, проверка тегов result=success/failure.
Не добавляй сторонних библиотек инструментирования без согласования.
```

---

## Шаг 10. Защита от кардинальности и накладных расходов

```text
Добавь защитный слой в отдельный @Configuration (ObservabilityConfig) в пакете config:
1. MeterFilter'ы: maximumAllowableTags для http.server.requests/http.client.requests по uri; ignoreTags для
   потенциально опасных тегов; deny для неиспользуемых метрик (оцени по /actuator/prometheus и предложи список,
   согласуй со мной).
2. ObservationPredicate: игнор actuator, health, swagger-ui (/swagger-ui/**, /v3/api-docs/**).
3. Отключи ненужные группы метрик через management.metrics.enable.* (обоснуй каждую, например tomcat, logback,
   если не нужны).
4. Метрики ExecutorService/@Async пулов.
5. Тест: после серии запросов с разными ID число уникальных значений тега uri не растёт.
6. Измерь размер вывода /actuator/prometheus (число серий) и приложи в отчёт; цель: до 3000 серий на инстанс.
```

---

## Шаг 11. Инфраструктура для разработки (Prometheus, Grafana, Tempo/Jaeger, OTel Collector)

```text
Посмотри существующий docker-compose/Makefile и добавь профиль compose `observability`:
1. Prometheus (scrape management-порта приложения с Basic-кредами из .env, scrape_interval 15s,
   включить exemplars/native histograms только если поддерживается выбранным стеком; проверь).
2. Grafana с provisioning datasources (Prometheus, Tempo, Loki при необходимости) и дашбордов из
   infra/observability/grafana/dashboards.
3. OpenTelemetry Collector (OTLP receiver -> Tempo/Jaeger), с tail-based сэмплингом (сохранять ошибки и
   трейсы дольше 1с, остальное 10%) в примере prod-конфига.
4. Tempo или Jaeger (выбери один, обоснуй). Логи: Loki + Promtail/Alloy по желанию, спроси меня.
5. Make-цели: make obs-up, make obs-down, make obs-open.
6. Переменные окружения приложения для подключения (OTLP endpoint, ENV, пароль scrape-пользователя) в
   .env.example, без реальных секретов.
7. README в infra/observability: как запустить, как найти трейс по traceId из лога.
Все версии образов закрепи (без latest).
```

---

## Шаг 12. Дашборды и алерты

```text
Подготовь артефакты как код (JSON/YAML в репозитории):
1. Дашборды Grafana:
   - Service RED (rate, errors, p50/p95/p99 по uri и статусам);
   - JVM (heap, GC паузы, потоки, CPU);
   - БД: Hikari по каждому пулу/шарду (active, pending, timeouts, usage), число запросов Hibernate;
   - Зависимости: MinIO, SMTP (rate/errors/latency);
   - Бизнес: заявки создано/закрыто, открытые по статусам, time to first response, ошибки аутентификации.
2. Правила алертов Prometheus (infra/observability/prometheus/alerts.yml):
   - error rate 5xx > 2% в течение 5 минут;
   - p99 latency > SLO в течение 10 минут;
   - hikaricp_connections_pending > 0 дольше 2 минут и hikaricp_connections_timeout_total растёт;
   - JVM heap > 90% 10 минут, GC pause доля времени;
   - readiness down, шард недоступен;
   - нет новых заявок при обычном рабочем трафике (осторожный порог, помечено как warning);
   - рост auth.attempts{result=failure} (возможный перебор).
   Каждому алерту: severity, summary, description, runbook_url (заглушка docs/runbooks/<name>.md).
3. Для каждого алерта создай docs/runbooks/<name>.md: что значит, как диагностировать (какие метрики/трейсы/логи
   смотреть), как митигировать.
4. Проверь promtool check rules, если доступен.
Определи 2-3 SLO (доступность API, latency создания заявки) и опиши их в docs/observability/slo.md.
```

---

## Шаг 13. Тесты наблюдаемости и стартовые проверки

```text
Собери набор тестов, гарантирующих, что наблюдаемость не деградирует:
1. Контекст-тест: приложение стартует с включённым tracing и prometheus, бины ObservationRegistry/MeterRegistry есть.
2. Тест на обязательные метрики: после запроса присутствуют http.server.requests, hikaricp.connections.active,
   jvm.memory.used, бизнес-метрики из шага 8.
3. Тест на запрещённые теги: нет тегов userId/email/ticketId в метриках (проход по всем Meter и их Tags).
4. Тест безопасности actuator (из шага 3) в составе CI.
5. Smoke-тест трейсинга: запрос создаёт спаны с общим traceId и дочерними спанами сервисного слоя.
6. Убедись, что тесты работают на H2/без внешней инфраструктуры и не требуют OTLP collector
   (экспорт отключён в тестовом профиле).
Запусти mvn verify, исправь замечания Spotless/Checkstyle.
```

---

## Шаг 14. Документация и Definition of Done

```text
1. Создай docs/observability/README.md: архитектура (метрики/трейсы/логи), как добавить новую метрику, правила
   кардинальности, каталог метрик, как отлаживать по traceId, как работать с алертами.
2. Добавь в PR-шаблон/чек-лист DoD: у новой фичи есть метрика или Observation, лог с контекстом без ПДн,
   алерт для критичных путей, обновлён дашборд, тест на метрику.
3. Добавь раздел в Makefile help про obs-* цели.
4. Составь итоговый отчёт: что внедрено, что осознанно не внедрено, известные ограничения (особенно по
   совместимости библиотек с Boot 4), рекомендации на следующий этап (tail sampling, exemplars, профилирование).
```
