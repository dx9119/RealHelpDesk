# ---------- STAGE 1: Build JAR ----------
#JDK
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Сборка проекта (config — конфиги Spotless/Checkstyle, нужные на фазе validate).
# Зависимости живут в кэше BuildKit, а не в слое образа: кэш переживает любую
# правку pom.xml, поэтому отдельный dependency:go-offline не нужен.
COPY pom.xml .
COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests

# Переименовываем jar (в target лежит ещё *.jar.original)
RUN cp $(ls target/*.jar | grep -v '\.original$' | head -n 1) app.jar

# Раскладываем jar на слои Spring Boot: слой зависимостей меняется только при
# правке pom.xml, слой приложения — при правке кода (sec.md §7.6).
RUN java -Djarmode=tools -jar app.jar extract --layers --destination /app/extracted

# Самоподписанный keystore при сборке НЕ генерируется: пароль не должен
# попадать в слои образа и в `docker history`. Он создаётся при старте
# контейнера — см. scripts/docker/entrypoint.sh.


# ---------- STAGE 2: Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Создаём группу и пользователя
RUN groupadd -r spring-group && \
    useradd -r -g spring-group \
            -d /app \
            -s /sbin/nologin \
            -c "Spring Boot application user" \
            spring-user

# Директория для SSL: keystore.p12 появляется здесь при старте контейнера,
# если свой файл не смонтирован volume (см. scripts/docker/entrypoint.sh)
RUN mkdir -p /app/ssl && chown spring-user:spring-group /app /app/ssl

# Точка входа: поднимает keystore (пароль берёт из окружения, не из сборки)
# и запускает приложение. --chown/--chmod задаются здесь же, чтобы не делать
# отдельный слой chown -R: он повторял бы содержимое слоёв jar (+73 МБ).
COPY --chown=spring-user:spring-group --chmod=755 scripts/docker/entrypoint.sh /app/entrypoint.sh

# Слои jar вместо одного жирного файла (sec.md §7.6). Порядок важен для кэша:
# реже меняющиеся слои идут первыми, слой приложения — последним.
# Boot 4.1.1 раскладывает jar так: application/app.jar (свой код и манифест
# с Class-Path) + dependencies/lib/*.jar (124 библиотеки); запуск — java -jar
# app.jar, манифест сам поднимает lib/.
COPY --from=build --chown=spring-user:spring-group /app/extracted/dependencies/ ./
COPY --from=build --chown=spring-user:spring-group /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring-user:spring-group /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring-user:spring-group /app/extracted/application/ ./

# Переключаемся на непривилегированного пользователя
USER spring-user

# Настройки JVM
# Размер Code Cache для JIT‑компиляции
ENV JAVA_RESERVED_CODE_CACHE_SIZE="240M"
# Ограничение Direct ByteBuffer (off‑heap)
ENV JAVA_MAX_DIRECT_MEMORY_SIZE="10M"
# Максимальный размер Metaspace — область, где JVM хранит метаданные классов.
ENV JAVA_MAX_METASPACE_SIZE="179M"
# Размер стека на поток (-Xss).
ENV JAVA_XSS="1M"
# Максимальный размер heap. Пустое значение (переопределяется в
# infrastructure/docker-compose.yaml) переключает entrypoint на -XX:MaxRAMPercentage.
ENV JAVA_XMX="345M"
# Доля heap от лимита памяти контейнера — используется, только если JAVA_XMX пуст.
# Имеет смысл только с mem_limit в infrastructure/docker-compose.yaml.
ENV JAVA_RAM_PERCENTAGE="75"
# Логирование GC и safepoint-пауз с ротацией файлов
ENV JAVA_GC_LOG_OPTS="-Xlog:gc*,safepoint:/tmp/gc.log::filecount=10,filesize=100M"
# Автоматический запуск Java Flight Recorder (профилирование в проде)
ENV JAVA_JFR_OPTS="-XX:StartFlightRecording=disk=true,dumponexit=true,filename=/tmp/,maxsize=10g,maxage=24h"
# Ошибка JVM в файл + heap dump при OOM + немедленный выход (важно для контейнеров)
ENV JAVA_ERROR_FILE_OPTS="-XX:ErrorFile=/tmp/java_error.log"
ENV JAVA_HEAP_DUMP_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
ENV JAVA_ON_OOM_OPTS="-XX:+ExitOnOutOfMemoryError"
# Native Memory Tracking (анализ нативной памяти JVM)
ENV JAVA_NMT_OPTS="-XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics"
# JMX: локальный доступ, без SSL и аутентификации
ENV JAVA_JMX_OPTS="-Djava.rmi.server.hostname=127.0.0.1 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dcom.sun.management.jmxremote.port=5005 \
    -Dcom.sun.management.jmxremote.rmi.port=5005"

# Предполагается работа на порту 8443
EXPOSE 8443

# Запуск приложения. Entrypoint сначала поднимает keystore, затем exec java.
ENTRYPOINT ["/app/entrypoint.sh"]
