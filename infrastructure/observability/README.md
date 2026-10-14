# Инфраструктура наблюдаемости (obs.md, шаги 10–11)

Стек для локальной разработки: метрики, трейсы и логи в одном месте. Все версии образов закреплены в
`infrastructure/docker-compose.observability.yaml` (без `latest`).

## Состав

| Сервис | Версия | Назначение | Порт (хост) |
|---|---|---|---|
| Prometheus | v3.15.0 | Скрейп `app:8081/actuator/prometheus` по HTTP Basic | 127.0.0.1:9090 |
| Grafana | 12.4.13 | UI: метрики, трейсы, логи; datasources через provisioning | 127.0.0.1:3001 |
| Tempo | 2.8.0 | Хранилище трейсов (OTLP-in, filesystem) | — (внутр. сеть) |
| OTel Collector | 0.162.0 | Приём OTLP от приложения → batch → Tempo | — |
| Loki | 3.5.0 | Хранилище логов (single-binary, filesystem) | — |
| Alloy | v1.12.2 | Сбор логов приложения из docker json-file → Loki | — |

## Запуск

```bash
make obs-up    # стек приложения + наблюдаемость (секреты скрейпа генерируются из .env)
make obs-down  # остановить (тома целы)
make obs-open  # открыть Grafana в браузере
```

`obs-up` перед `compose up` генерирует `observability/prometheus/secrets/metrics_{user,password}`
из `scripts/docker/stack.env` (METRICS_USER) и `infrastructure/.env` (METRICS_PASSWORD) —
Prometheus не умеет env-подстановку в конфиге, поэтому креды идут файлами.

Логин Grafana: `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` из `infrastructure/.env`
(по умолчанию admin/admin — задайте свой перед использованием). Datasources (Prometheus, Tempo, Loki)
подключаются автоматически через `grafana/provisioning/`; дашборды лежат в `grafana/dashboards/`
и провижинятся в папку RealHelpDesk.

## Дашборды

**RealHelpDesk — Обзор** (`grafana/dashboards/realhelpdesk-overview.json`, uid `realhelpdesk-overview`) —
сводный дашборд на 23 панели, папка RealHelpDesk, URL: `http://127.0.0.1:3001/d/realhelpdesk-overview`:

- **Service RED**: RPS, доля 5xx (пороги 2%/5% — как в алертах obs.md), p95/p99, RPS и p95 по
  эндпоинтам, распределение статусов.
- **Бизнес**: попытки аутентификации (failure подсвечивается красным), открытые заявки по статусам,
  скорость создания заявок, TTFR p95, почта (mail.sent + SMTP p95), ticket.create p95, переходы
  статусов, логи по уровням.
- **JVM**: heap used/max, GC-паузы, потоки, CPU процесса/системы.
- **БД и фон**: пул соединений (active/idle/max), Hibernate queries/s, очередь планировщика
  taskScheduler, аптайм.

Панели используют канонические имена из `docs/observability/metrics.md`; перцентильные гистограммы
собираются через `histogram_quantile`. Изменения дашбордов через UI сохраняются (allowUiUpdates),
для переноса на другой стенд скопируйте JSON обратно в `grafana/dashboards/`.

## Потоки данных

```
приложение ──OTLP (traces)──▶ otel-collector ──▶ tempo
приложение ──stdout json──▶ alloy ──▶ loki
приложение ──HTTP Basic──▶ prometheus (метрики, management-порт 8081)
Grafana ◀── datasources: prometheus (метрики), tempo (трейсы), loki (логи)
```

В оверлее приложение получает `OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces`; без оверлея
(обычный `make up`) endpoint — `localhost:4317` (gRPC) из `stack.env`, экспорта трейсов нет (соединение
отклоняется, приложение работает).

## Как найти трейс по traceId из лога

1. Возьмите `traceId` (32 hex) из строки лога приложения: колонка после `role`
   (`… | INFO | … | <traceId> | <spanId> | <logger> | <msg>`).
2. **Grafana → Explore → Tempo** (источник Tempo): вкладка Search/TraceQL → вставить traceId →
   откроется waterfall спанов.
3. Или наоборот: **Explore → Loki** → запрос
   `{container_label_com_docker_compose_service="app"} |= "<traceId>"` → в колонке слева от лога
   появится ссылка **TraceID** (derived-поле provisioning'а) → переход к трейсу.
4. Из трейса в Loki — кнопка «Logs for this span» (tracesToLogs настроен на тот же traceId).

Связка метрики → трейс: в Tempo включён serviceMap (через Prometheus); exemplars в скрейпе
не включены — переход «метрика → конкретный trace» пока недоступен (см. «Известные ограничения»).

## Трассировка: сэмплинг

- Локально доля запросов задаётся `TRACING_SAMPLING_PROBABILITY` (`stack.env`, по умолчанию 1.0).
- В `otel-collector/config-prod.yaml` — пример прод-конфига с хвостовым сэмплингом: ошибки и трейсы
  дольше 1 s сохраняются целиком, остальное — 10%. Подключается монтированием файла поверх
  `config.yaml` в prod-оверлее.

## Известные ограничения

- **Exemplars не включены**: для `percentiles-histogram` в Micrometer exemplars требуют
  OpenTelemetry-трейсинг в том же потоке; переход с метрики на конкретный trace работает через
  Tempo serviceMap и логи, а не через exemplars.
- **Loki парсит текстовые логи** (паттерн `logback-spring.xml`, а не JSON): derived-поле строится
  регуляркой по колонке traceId; при смене паттерна логов поправить `grafana/provisioning/datasources/datasources.yaml`.
- **Alloy ходит в docker.sock от root** — локальный dev-стенд; в проде заменить на Alloy с
  read-only сокетом и без root.
- **Retention**: Tempo 48 h (`tempo/tempo.yaml`), Loki 7 d (`loki/loki.yaml`), Prometheus без
  retention-флага (2 недели дефолтом).

## Полезные проверки

```bash
# скрейп Prometheus: up=1 и бизнес-метрики
curl -s localhost:9090/api/v1/query?query=up
# трейсы в Tempo
curl -s http://localhost:3200/ready
# Loki готов
curl -s http://localhost:3100/ready
# логи приложения в Loki
curl -s -G http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={container_label_com_docker_compose_service="app"}' | head -c 400
```
