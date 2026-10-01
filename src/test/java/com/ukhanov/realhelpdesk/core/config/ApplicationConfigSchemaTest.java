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
 * Контракт конфигурации приложения: {@code application.properties} — только схема из обязательных {@code ${VAR}}, а значения лежат в двух
 * местах: {@code docker/app.env} (продукт) и {@code services.app.environment} в {@code docker-compose.yaml} (окружение и секреты).
 *
 * <p>
 * Файл читается напрямую по пути {@code src/main/resources/...}, а не с classpath, поэтому на результат не влияет даже
 * {@code src/test/resources/application.properties}, который перекрывает main-конфиг в тестовом класслоаде.
 * </p>
 */
@DisplayName("Конфигурация: схема application.properties, docker-compose.yaml и docker/app.env")
class ApplicationConfigSchemaTest {

    private static final Path APPLICATION_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yaml");
    private static final Path APP_ENV = Path.of("docker", "app.env");
    /** Ровно ${VAR}: без дефолта после двоеточия и без литерала до/после. */
    private static final Pattern SCHEMA_VALUE = Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*}");
    private static final Pattern SCHEMA_VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)}");

    /**
     * Ключи {@code services.app.environment}, которых нет в схеме приложения: они переопределяют {@code ENV} образа (JVM-флаги, см. sec.md
     * §7.8) и приложению не нужны. В {@code docker/app.env} такого быть не должно.
     */
    private static final String NON_SCHEMA_ENV_PREFIX = "JAVA_";

    @Test
    @DisplayName("application.properties — чистая схема: только обязательные ${VAR}, без дефолтов и литералов")
    void applicationPropertiesIsSchemaWithoutValues() throws Exception {
        Properties schema = loadSchema();

        List<String> notSchema = schema.stringPropertyNames().stream().sorted()
                .filter(name -> !SCHEMA_VALUE.matcher(schema.getProperty(name)).matches())
                .map(name -> name + "=" + schema.getProperty(name)).toList();

        assertThat(schema.stringPropertyNames()).as("схема должна содержать свойства").isNotEmpty();
        assertThat(notSchema).as("значения вне вида ${VAR} (дефолт или литерал в коде)").isEmpty();
    }

    @Test
    @DisplayName("Переменные схемы и ключи docker-compose.yaml + docker/app.env совпадают один в один")
    void everySchemaVariableIsDeclaredInDockerComposeOrAppEnv() throws Exception {
        Properties schema = loadSchema();

        Set<String> schemaVariables = schema.stringPropertyNames().stream().map(schema::getProperty)
                .flatMap(value -> variables(value).stream()).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> composeKeys = composeAppEnvironment();
        Set<String> appEnvKeys = appEnvKeys();

        assertThat(composeKeys.stream().filter(appEnvKeys::contains).toList())
                .as("одно значение может быть задано только в одном месте: docker-compose.yaml или docker/app.env").isEmpty();

        Set<String> declaredValues = new LinkedHashSet<>(composeKeys);
        declaredValues.addAll(appEnvKeys);
        assertThat(declaredValues).as("схема application.properties и ключи compose + app.env")
                .containsExactlyInAnyOrderElementsOf(schemaVariables);
    }

    private Properties loadSchema() throws Exception {
        assertThat(Files.exists(APPLICATION_PROPERTIES)).as(APPLICATION_PROPERTIES.toString()).isTrue();
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(APPLICATION_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
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

    @SuppressWarnings("unchecked")
    private Set<String> composeAppEnvironment() throws Exception {
        assertThat(Files.exists(DOCKER_COMPOSE)).as(DOCKER_COMPOSE.toString()).isTrue();
        try (var reader = Files.newBufferedReader(DOCKER_COMPOSE, StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Yaml().load(reader);
            Map<String, Object> services = (Map<String, Object>) root.get("services");
            Map<String, Object> app = (Map<String, Object>) services.get("app");
            Map<String, Object> environment = (Map<String, Object>) app.get("environment");

            assertThat(environment).as("services.app.environment в docker-compose.yaml").isNotNull();
            return environment.keySet().stream().filter(key -> !key.startsWith(NON_SCHEMA_ENV_PREFIX))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /**
     * Ключи {@code docker/app.env}: только имя до первого {@code =}, комментарии и пустые строки пропускаются. Значения здесь не
     * проверяются — их смысл зависит от ключа, а не от формы.
     */
    private Set<String> appEnvKeys() throws Exception {
        assertThat(Files.exists(APP_ENV)).as(APP_ENV.toString()).isTrue();
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(APP_ENV, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            assertThat(separator).as("строка без = : " + line).isPositive();
            keys.add(trimmed.substring(0, separator).trim());
        }
        assertThat(keys).as(APP_ENV.toString()).isNotEmpty();
        return keys;
    }
}
