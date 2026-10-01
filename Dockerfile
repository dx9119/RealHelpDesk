# ---------- STAGE 1: Build JAR ----------
#JDK
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Подготавливаем репозиторий Maven
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Сборка проекта (config — конфиги Spotless/Checkstyle, нужные на фазе validate)
COPY src ./src
COPY config ./config
RUN mvn -q package -DskipTests

# Переименовываем jar (в target лежит ещё *.jar.original)
RUN cp $(ls target/*.jar | grep -v '\.original$' | head -n 1) app.jar

# Самоподписанный keystore для HTTPS.
# В репозитории *.p12 не хранится (см. .gitignore), поэтому он генерируется
# при сборке. Для продакшна положите свой файл в /app/ssl/keystore.p12 (volume).
# Пароль и alias приходят из docker-compose.yaml (build.args) — дефолтов нет.
ARG KEY_STORE_PASS
ARG KEY_STORE_ALIAS
RUN keytool -genkeypair \
        -alias ${KEY_STORE_ALIAS} \
        -keyalg RSA -keysize 2048 \
        -storetype PKCS12 \
        -keystore /app/keystore.p12 \
        -storepass ${KEY_STORE_PASS} \
        -dname "CN=localhost" \
        -validity 3650


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

# Директория для SSL и самоподписанный keystore из этапа сборки
RUN mkdir -p /app/ssl
COPY --from=build /app/keystore.p12 /app/ssl/keystore.p12

# Файл приложения: java -jar app.jar запускает жирный jar
# (слои Spring Boot при таком запуске не используются, поэтому этап
# извлечения слоёв не нужен — он оставлял в образе app.jsa/app.jar)
COPY --from=build /app/app.jar app.jar

# Даём права пользователю spring-user на всё нужное
RUN chown -R spring-user:spring-group /app

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
# Максимальный размер heap (-Xmx). Главный лимит памяти приложения внутри контейнера.
ENV JAVA_XMX="345M"
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

# Запуск приложения
ENTRYPOINT exec java \
    -XX:ReservedCodeCacheSize=${JAVA_RESERVED_CODE_CACHE_SIZE} \
    -XX:MaxDirectMemorySize=${JAVA_MAX_DIRECT_MEMORY_SIZE} \
    -XX:MaxMetaspaceSize=${JAVA_MAX_METASPACE_SIZE} \
    -Xss${JAVA_XSS} \
    -Xmx${JAVA_XMX} \
    ${JAVA_HEAP_DUMP_OPTS} \
    ${JAVA_ON_OOM_OPTS} \
    ${JAVA_ERROR_FILE_OPTS} \
    ${JAVA_NMT_OPTS} \
    ${JAVA_GC_LOG_OPTS} \
    ${JAVA_JFR_OPTS} \
    ${JAVA_JMX_OPTS} \
    -jar app.jar
