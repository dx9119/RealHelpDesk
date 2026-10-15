# Runbook: AppUnreachable

**Алерт**: `up{job="realhelpdesk"} == 0` в течение 2 минут (`severity: critical`) —
Prometheus не может проскрейпить управляющий порт приложения.

> **Примечание**: в текущем стенде отдельного
> scrape-цела для `/actuator/health/readiness` нет — health-группы доступны на
> управляющем порту (8081), а `up` отражает и сам контейнер, и закрытый
> management-порт. Readiness в k8s-оверлее имеет смысл скрейпить отдельным job'ом.

## Что это значит

Приложение не отвечает на управляющем порту: процесс упал/OOM-restart, завис,
или закрыт порт 8081 (конфигурация/сеть).

## Диагностика

1. **Состояние контейнера**: `docker ps` (Restarting?), `docker logs <app> --tail 100` —
   стектрейс OOM, `APPLICATION FAILED TO START`.
2. **Порты**: `docker exec <app> curl -s localhost:8081/actuator/health/readiness`.
3. **Логи Loki**: `{container_label_com_docker_compose_service="app"} |~ "ERROR|FATAL"`.
4. **Ресурсы**: `docker stats` — OOM-kill по памяти (лимит mem_limit).
5. **Рестарты**: `docker inspect` → RestartCount; корреляция с jvm-heap-high/деплоем.

## Митигация

- OOM-kill: поднять лимит памяти / -Xmx, разобрать утечку (jvm-heap-high).
- Ошибка старта: откат конфигурации/миграций Flyway.
- Зависание: thread dump (`/actuator/threaddump` на management-порте), найти блокировку.
- После восстановления — проверить, что `up == 1` и метрики снова идут, затем
  разобрать корневую причину по логам до рестарта.
