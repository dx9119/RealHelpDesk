# ---------- STAGE 1: Build JAR ----------
#JDK
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Подготавлеваем репозиторий Maven
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Сборка проекта
COPY src ./src
RUN mvn -q package -DskipTests


# ---------- STAGE 2: Extract Spring Boot Layers ----------
FROM eclipse-temurin:21-jre AS layers

WORKDIR /app

# Забираем прошлый билд
COPY --from=build /app/target/*.jar app.jar

# Извлекаем слои Spring Boot
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted


# ---------- STAGE 3: Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Создаём группу и пользователя
RUN groupadd -r spring-group && \
    useradd -r -g spring-group \
            -d /app \
            -s /sbin/nologin \
            -c "Spring Boot application user" \
            spring-user

# Создаём директорию для SSL и копируем сертификат
RUN mkdir -p /app/ssl
COPY ./keystore.p12 /app/ssl/keystore.p12

# Копируем извлечённые слои приложения в рабочую директорию образа /app
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./

# Генерация CDS-архива (Class Data Sharing) - CDS — это механизм, при котором JVM
# заранее сохраняет скомпилированные метаданные классов, чтобы при следующем
# запуске не тратить время на их загрузку и верификацию.
# Эта команда запускает Spring Boot на секунду, даёт JVM загрузить
# классы, затем завершает приложение и сохраняет CDS‑архив app.jsa
RUN java -XX:ArchiveClassesAtExit=app.jsa \
         -Dspring.context.exit=onRefresh \
         -jar app.jar & sleep 1 && kill $! || true

# Даём права пользователю spring-user на всё нужное
RUN chown -R spring-user:spring-group /app

# Переключаемся на непривилегированного пользователя
USER spring-user

# Настройки JVM
# Размер Code Cache для JIT‑компилятора
ENV JAVA_RESERVED_CODE_CACHE_SIZE="240M"
# Ограничение Direct ByteBuffer (off‑heap)
ENV JAVA_MAX_DIRECT_MEMORY_SIZE="10M"
# Максимальный размер Metaspace — область, где JVM хранит метаданные классов.
ENV JAVA_MAX_METASPACE_SIZE="179M"
# Размер стека на поток (-Xss).
ENV JAVA_XSS="1M"
# Максимальный размер heap (-Xmx). Главный лимит памяти приложения внутри контейнера.
ENV JAVA_XMX="345M"

# Подключение CDS-архива (ускоряет запуск JVM) + лог загрузки классов
ENV JAVA_CDS_OPTS="-XX:SharedArchiveFile=app.jsa -Xlog:class+load:file=/tmp/classload.log"
# Куда JVM пишет фатальные ошибки (hs_err_pid.log)
ENV JAVA_ERROR_FILE_OPTS="-XX:ErrorFile=/tmp/java_error.log"
# Создание heap dump при OOM + путь сохранения
ENV JAVA_HEAP_DUMP_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
# Немедленный выход JVM при OOM (важно для контейнеров и оркестраторов)
ENV JAVA_ON_OOM_OPTS="-XX:+ExitOnOutOfMemoryError"
# Включение Native Memory Tracking (анализ нативной памяти JVM)
ENV JAVA_NMT_OPTS="-XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics"
# Логирование GC и safepoint-пауз с ротацией файлов
ENV JAVA_GC_LOG_OPTS="-Xlog:gc*,safepoint:/tmp/gc.log::filecount=10,filesize=100M"
# Автоматический запуск Java Flight Recorder (профилирование в проде)
ENV JAVA_JFR_OPTS="-XX:StartFlightRecording=disk=true,dumponexit=true,filename=/tmp/,maxsize=10g,maxage=24h"
# Настройки JMX: локальный доступ, без SSL и аутентификации, фиксированный порт
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
    ${JAVA_CDS_OPTS} \
    -jar app.jar