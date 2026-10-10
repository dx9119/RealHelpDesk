package com.ukhanov.realhelpdesk.core.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт конфигурации приложения: {@code application.properties} и {@code application-domains.properties} (домены) — только схема из
 * обязательных {@code ${VAR}}, а значения лежат в двух местах: {@code scripts/docker/stack.env} (весь несекретный набор — продукт,
 * окружение, инфраструктура) и {@code services.app.environment} в {@code infrastructure/docker-compose.yaml} (только секреты через
 * {@code ${VAR}} из {@code infrastructure/.env}).
 *
 * <p>
 * Файлы читаются напрямую по пути {@code src/main/resources/...}, а не с classpath, поэтому на результат не влияет даже
 * {@code src/test/resources/application.properties}, который перекрывает main-конфиг в тестовом класслоаде.
 * </p>
 */
@DisplayName("Конфигурация: схема application*.properties, infrastructure/docker-compose.yaml и scripts/docker/stack.env")
class ApplicationConfigSchemaTest {

    private static final Path APPLICATION_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final Path APPLICATION_DOMAINS_PROPERTIES = Path.of("src", "main", "resources", "application-domains.properties");
    private static final Path DOCKER_COMPOSE = Path.of("infrastructure", "docker-compose.yaml");
    private static final Path STACK_ENV = Path.of("scripts", "docker", "stack.env");
    /** Ровно ${VAR}: без дефолта после двоеточия и без литерала до/после. */
    private static final Pattern SCHEMA_VALUE = Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*}");
    private static final Pattern SCHEMA_VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)}");

    /**
     * Ключи, которым позволено содержать литерал: {@code spring.config.import} подключает application-domains.properties — значение это
     * адрес файла, а не переменная окружения. {@code spring.jpa.hibernate.ddl-auto} закреплён в коде (validate): режим, изменяющий схему,
     * нельзя оставить на откуп окружения — им управляют только миграции Liquibase; исключение отдельно охраняется
     * {@link #ddlAutoIsPinnedToValidateAndNeverDestructive()}.
     */
    private static final Set<String> NON_SCHEMA_KEYS = Set.of("spring.config.import", "spring.jpa.hibernate.ddl-auto");

    /**
     * Конфигурация среды: файлы схемы и места со значениями. В них не должно появиться деструктивного режима ddl-auto — иначе рестарт
     * уничтожит данные. Тестовые конфиги и {@code @DataJpaTest(properties=...)} не входят: там схему создаёт H2, это не прод.
     */
    private static final List<Path> ENVIRONMENT_FILES = List.of(Path.of("src", "main", "resources", "application.properties"),
            Path.of("src", "main", "resources", "application-domains.properties"),
            Path.of("src", "main", "resources", "application-debug.properties"),
            Path.of("src", "main", "resources", "application-prod.properties"), DOCKER_COMPOSE, STACK_ENV);

    /** Любой ddl-auto в конфигурации среды: имя ключа и режим после {@code =}/{@code :}, кавычки опциональны. */
    private static final Pattern DDL_AUTO_ASSIGNMENT = Pattern.compile("(?i)ddl[-_]auto\\s*[=:]\\s*\"?([a-z-]+)\"?");

    /**
     * Ключи {@code services.app.environment} и {@code scripts/docker/stack.env}, которых нет в схеме приложения. {@code JAVA_} — JVM-флаги,
     * переопределяющие {@code ENV} образа (sec.md §7.8); {@code INFRA} — инфраструктурные переменные compose (БД и прокси), которые нужны
     * контейнерам, но не приложению.
     */
    private static final String NON_SCHEMA_ENV_PREFIX = "JAVA_";
    private static final Set<String> NON_SCHEMA_ENV_KEYS = Set.of("POSTGRES_USER", "POSTGRES_DB", "SHARD_DB", "SHARD_DB_USER", "PORT");

    @Test
    @DisplayName("application*.properties — чистая схема: только обязательные ${VAR}, без дефолтов и литералов")
    void applicationPropertiesIsSchemaWithoutValues() throws Exception {
        Properties schema = loadSchema();

        List<String> notSchema = schema.stringPropertyNames().stream().sorted().filter(name -> !NON_SCHEMA_KEYS.contains(name))
                .filter(name -> !SCHEMA_VALUE.matcher(schema.getProperty(name)).matches())
                .map(name -> name + "=" + schema.getProperty(name)).toList();

        assertThat(schema.stringPropertyNames()).as("схема должна содержать свойства").isNotEmpty();
        assertThat(notSchema).as("значения вне вида ${VAR} (дефолт или литерал в коде)").isEmpty();
    }

    @Test
    @DisplayName("Переменные схемы (оба файла) и ключи infrastructure/docker-compose.yaml + scripts/docker/stack.env совпадают один в один")
    void everySchemaVariableIsDeclaredInComposeOrStackEnv() throws Exception {
        Properties schema = loadSchema();

        Set<String> schemaVariables = schema.stringPropertyNames().stream().map(schema::getProperty)
                .flatMap(value -> variables(value).stream()).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> composeKeys = composeAppEnvironment();
        Set<String> stackEnvKeys = appKeysOfStackEnv();

        assertThat(composeKeys.stream().filter(stackEnvKeys::contains).toList())
                .as("одно значение может быть задано только в одном месте: scripts/docker/stack.env или services.app.environment")
                .isEmpty();

        Set<String> declaredValues = new LinkedHashSet<>(composeKeys);
        declaredValues.addAll(stackEnvKeys);
        assertThat(declaredValues).as("схема application.properties + application-domains.properties и ключи compose + stack.env")
                .containsExactlyInAnyOrderElementsOf(schemaVariables);
    }

    @Test
    @DisplayName("ddl-auto закреплён в коде: validate, деструктивный режим не появится в конфигурации среды")
    void ddlAutoIsPinnedToValidateAndNeverDestructive() throws Exception {
        Properties schema = loadSchema();

        assertThat(schema.getProperty("spring.jpa.hibernate.ddl-auto")).as("режим схемы задан в коде, а не в окружении")
                .isEqualTo("validate");

        assertThat(composeAppEnvironment()).as("переменной ddl-auto в infrastructure/docker-compose.yaml быть не должно")
                .doesNotContain("JPA_DDL_AUTO");
        assertThat(appKeysOfStackEnv()).as("переменной ddl-auto в scripts/docker/stack.env быть не должно").doesNotContain("JPA_DDL_AUTO");

        for (Path file : ENVIRONMENT_FILES) {
            assertThat(Files.exists(file)).as(file.toString()).isTrue();
            String content = Files.readString(file, StandardCharsets.UTF_8);

            Matcher assignment = DDL_AUTO_ASSIGNMENT.matcher(content);
            while (assignment.find()) {
                assertThat(assignment.group(1)).as(file.toString() + ": режим ddl-auto").isEqualTo("validate");
            }

            assertThat(content).as(file.toString()).doesNotContain("create-drop").doesNotContain("drop-create");
        }
    }

    /** Оба файла схемы: основной конфиг и домены (spring.config.import). Дубли ключей между ними — ошибка. */
    private Properties loadSchema() throws Exception {
        Properties properties = new Properties();
        for (Path file : List.of(APPLICATION_PROPERTIES, APPLICATION_DOMAINS_PROPERTIES)) {
            assertThat(Files.exists(file)).as(file.toString()).isTrue();
            Properties part = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                part.load(reader);
            }
            Set<String> duplicates = new LinkedHashSet<>(part.stringPropertyNames());
            duplicates.retainAll(properties.stringPropertyNames());
            assertThat(duplicates).as("ключ объявлен в обоих файлах схемы: " + file).isEmpty();
            properties.putAll(part);
        }
        return properties;
    }

    private Set<String> variables(String value) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = SCHEMA_VARIABLE.matcher(value);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /** Ключи {@code services.app.environment} — только секреты (ссылки на переменные хоста); JVM-флаги туда не входят. */
    @SuppressWarnings("unchecked")
    private Set<String> composeAppEnvironment() throws Exception {
        assertThat(Files.exists(DOCKER_COMPOSE)).as(DOCKER_COMPOSE.toString()).isTrue();
        try (var reader = Files.newBufferedReader(DOCKER_COMPOSE, StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Yaml().load(reader);
            Map<String, Object> services = (Map<String, Object>) root.get("services");
            Map<String, Object> app = (Map<String, Object>) services.get("app");
            Map<String, Object> environment = (Map<String, Object>) app.get("environment");

            assertThat(environment).as("services.app.environment в infrastructure/docker-compose.yaml (только секреты)").isNotNull();
            return environment.keySet().stream().filter(key -> !key.startsWith(NON_SCHEMA_ENV_PREFIX))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /**
     * Ключи приложения в {@code scripts/docker/stack.env}: только имя до первого {@code =}, комментарии и пустые строки пропускаются.
     * Значения здесь не проверяются — их смысл зависит от ключа, а не от формы. JVM-флаги и инфраструктурные ключи compose из контракта
     * исключаются.
     */
    private Set<String> appKeysOfStackEnv() throws Exception {
        assertThat(Files.exists(STACK_ENV)).as(STACK_ENV.toString()).isTrue();
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(STACK_ENV, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            assertThat(separator).as("строка без = : " + line).isPositive();
            keys.add(trimmed.substring(0, separator).trim());
        }
        assertThat(keys).as(STACK_ENV.toString()).isNotEmpty();
        return keys.stream().filter(key -> !key.startsWith(NON_SCHEMA_ENV_PREFIX)).filter(key -> !NON_SCHEMA_ENV_KEYS.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
