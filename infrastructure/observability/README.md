# Инфраструктура наблюдаемости

Стек для локальной разработки: метрики, трейсы и логи в одном месте. Все версии образов закреплены в
`infrastructure/docker-compose.yaml` (профиль `monitoring`, без `latest`).

## Состав

| Сервис | Версия | Назначение | Порт (хост) |
|---|---|---|---|
| Mimir | 2.16.0 | Хранение метрик, Prometheus-совместимый API (`/prometheus`), ruler алертов | 127.0.0.1:9090 |
| Grafana | 12.4.13 | UI: метрики, трейсы, логи; datasources через provisioning | 127.0.0.1:3001 |
| Tempo | 2.8.0 | Хранилище трейсов (OTLP-in, filesystem) | — (внутр. сеть) |
| OTel Collector | 0.162.0 | Приём OTLP от приложения → batch → Tempo | — |
| Loki | 3.5.0 | Хранилище логов (single-binary, filesystem) | — |
| Alloy | v1.12.2 | Сбор логов приложения из docker json-file → Loki; скрейп метрик app:8081 → remote_write в Mimir | — |

## Запуск

```bash
make obs-up    # стек приложения + наблюдаемость
make obs-down  # остановить (тома целы)
make obs-open  # открыть Grafana в браузере
```

Креды скрейпа (`METRICS_USER` и `METRICS_PASSWORD` из `infrastructure/.env`)
приходят в контейнер alloy env-файлом; `config.alloy`
читает их через `sys.env()` для basic_auth при скрейпе `app:8081` — на хосте
файлов с секретами не остаётся.

Логин Grafana: `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` из `infrastructure/.env`
(по умолчанию admin/admin — задайте свой перед использованием). Datasources (Mimir, Tempo, Loki)
подключаются автоматически через `grafana/provisioning/`; дашборды лежат в `grafana/dashboards/`
и провижинятся в папку RealHelpDesk.

## Дашборды

**RealHelpDesk — Обзор** (`grafana/dashboards/realhelpdesk-overview.json`, uid `realhelpdesk-overview`) —
сводный дашборд на 23 панели, папка RealHelpDesk, URL: `http://127.0.0.1:3001/d/realhelpdesk-overview`:

- **Service RED**: RPS, доля 5xx (пороги 2%/5% — как в правилах алертов), p95/p99, RPS и p95 по
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

## Алерты

Правила — `mimir/rules/anonymous/realhelpdesk.yml` (Prometheus-формат, монтируется ruler'у Mimir
read-only; раскладка local store `<dir>/<tenant>/<файл>`, tenant = `anonymous`). 8 алертов в 5 группах
(api, database, jvm, availability, security); каждому —
severity/summary/description/runbook_url. Runbooks: `docs/runbooks/*.md`. SLO: `docs/observability/slo.md`.

| Алерт | Порог | Runbook |
|---|---|---|
| ApiHighErrorRate | 5xx > 2% / 5 мин (critical) | `docs/runbooks/api-high-error-rate.md` |
| ApiHighLatencyP99 | p99 > 500 мс (SLO) / 10 мин | `docs/runbooks/api-high-latency.md` |
| DatabasePoolSaturation | active/max > 90% / 2 мин | `docs/runbooks/db-pool-saturation.md` |
| JvmHeapHigh | heap > 90% / 10 мин | `docs/runbooks/jvm-heap-high.md` |
| JvmGcOverheadHigh | GC > 10% времени / 10 мин | `docs/runbooks/jvm-gc-overhead.md` |
| AppUnreachable | `up == 0` / 2 мин (critical) | `docs/runbooks/app-unreachable.md` |
| NoNewTickets | нет заявок 2 ч в рабочее время | `docs/runbooks/no-new-tickets.md` |
| AuthFailuresSpike | failure > 6/мин / 5 мин | `docs/runbooks/auth-failures-spike.md` |

> В Boot 4.1 пул экспортируется как `jdbc_connections_*` — метрик `hikaricp_connections_pending/timeout`
> в скрейпе нет; вместо pending используется насыщение пула (см. runbook db-pool-saturation).

Проверка и перезагрузка правил:

```bash
# синтаксис (promtool из образа, без локальной установки)
docker run --rm -v $PWD/infrastructure/observability/mimir/rules/anonymous/realhelpdesk.yml:/alerts.yml:ro \
  --entrypoint promtool prom/prometheus:v3.15.0 check rules /alerts.yml
# перечитать правила после правок (рестарт контейнера) — или make obs-reload
make obs-reload
# текущие состояния алертов — или make obs-alerts
curl -s localhost:9090/prometheus/api/v1/alerts
```

## Потоки данных

```
приложение ──OTLP (traces)──▶ otel-collector ──▶ tempo
приложение ──stdout json──▶ alloy ──▶ loki
приложение ──HTTP Basic (метрики, management-порт 8081)──▶ alloy ──remote_write──▶ mimir
Grafana ◀── datasources: mimir (метрики), tempo (трейсы), loki (логи)
```

Без наблюдаемости (обычный `make up`) endpoint — `localhost:4317` (дефолт
compose-интерполяции), экспорта трейсов нет (соединение отклоняется, приложение
работает). `make obs-up` и `make up-load`/`make load-smoke` передают
`OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces` инлайном, поэтому
в комбинации «obs + load» endpoint'ы совпадают — экспорт трейсов не ломается
при пересоздании app ни одной из команд. Постоянный адрес collector'а для всех
запусков — `OTLP_TRACING_ENDPOINT` в `infrastructure/.env`.

## Как найти трейс по traceId из лога

1. Возьмите `traceId` (32 hex) из строки лога приложения: колонка после `role`
   (`… | INFO | … | <traceId> | <spanId> | <logger> | <msg>`).
2. **Grafana → Explore → Tempo** (источник Tempo): вкладка Search/TraceQL → вставить traceId →
   откроется waterfall спанов.
3. Или наоборот: **Explore → Loki** → запрос
   `{container_label_com_docker_compose_service="app"} |= "<traceId>"` → в колонке слева от лога
   появится ссылка **TraceID** (derived-поле provisioning'а) → переход к трейсу.
4. Из трейса в Loki — кнопка «Logs for this span» (tracesToLogs настроен на тот же traceId).

Связка метрики → трейс: в Tempo включён serviceMap (через Mimir); exemplars в скрейпе
не включены — переход «метрика → конкретный trace» пока недоступен (см. «Известные ограничения»).

## Трассировка: сэмплинг

- Локально доля запросов задаётся `TRACING_SAMPLING_PROBABILITY` (`infrastructure/.env`, по умолчанию 1.0).
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
- **Retention**: Tempo 48 h (`tempo/tempo.yaml`), Loki 7 d (`loki/loki.yaml`), Mimir 15 d
  (`mimir/mimir.yaml`, compactor; как дефолт прежнего Prometheus).

## Полезные проверки

```bash
# скрейп дошёл в Mimir: up=1 и бизнес-метрики (Prometheus-совместимый API)
curl -s 'localhost:9090/prometheus/api/v1/query?query=up'
# трейсы в Tempo
curl -s http://localhost:3200/ready
# Loki готов
curl -s http://localhost:3100/ready
# логи приложения в Loki
curl -s -G http://localhost:3100/loki/api/v1/query_range --data-urlencode 'query={container_label_com_docker_compose_service="app"}' | head -c 400
```
