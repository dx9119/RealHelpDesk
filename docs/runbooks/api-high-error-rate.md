# Runbook: ApiHighErrorRate

**Алерт**: доля HTTP-ответов 5xx > 2% в течение 5 минут (`severity: critical`).
Метрика: `http_server_requests_seconds_count{status=~"5.."}`.

## Что это значит

Более 2% запросов к API завершаются серверной ошибкой (500/502/503/504). Для helpdesk-системы
это означает, что заявки не создаются/не обновляются, агенты не могут работать.

## Диагностика

1. **Дашборд**: RealHelpDesk — Обзор → панели «Ошибки 5xx, %», «Статусы ответов»,
   «RPS по эндпоинтам» — локализуйте uri с ошибками.
2. **Логи** (Grafana → Explore → Loki):
   `{container_label_com_docker_compose_service="app"} |= "ERROR"`
   — в stacktrace ищите `exception` (NPE, constraint violation, таймаут БД/MinIO/SMTP).
3. **Трейсы** (Explore → Tempo, фильтр critical errors): спаны с `status:error` —
   видно, на каком слое (repository/service/http) падает запрос.
4. **Зависимости**: `jdbc_connections_active` (БД), health-группа `/actuator/health/smtp`
   (почта), `/actuator/health/minio` (хранилище). Логи Alloy: таймауты connect.
5. **Корреляция**: если ошибки совпали с деплоем/перезапуском — почти наверняка регрессия.

## Митигация

- Откат последнего релиза (если корреляция по времени).
- Если БД: проверить deadlock-и в логах postgres, размер пула (`jdbc_connections_max`).
- Если SMTP/MinIO: health-группа уйдёт в DOWN — заявки при этом продолжают создаваться
  (отправка асинхронная), временно допустимо.
- Если перегрузка: масштабировать реплики app (stateless), кэш тяжёлых чтений.
