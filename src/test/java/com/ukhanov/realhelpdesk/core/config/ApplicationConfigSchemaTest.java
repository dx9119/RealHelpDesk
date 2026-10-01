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
 * Контракт конфигурации приложения: {@code application.properties} — только схема из обязательных {@code ${VAR}}, а значения живут в
 * {@code docker-compose.yaml}.
 *
 * <p>
 * Файл читается напрямую по пути {@code src/main/resources/...}, а не с classpath, поэтому на результат не влияет даже
 * {@code src/test/resources/application.properties}, который перекрывает main-конфиг в тестовом класслоаде.
 * </p>
 */
@DisplayName("Конфигурация: схема application.properties и docker-compose.yaml")
class ApplicationConfigSchemaTest {

    private static final Path APPLICATION_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yaml");
    /** Ровно ${VAR}: без дефолта после двоеточия и без литерала до/после. */
    private static final Pattern SCHEMA_VALUE = Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*}");
    private static final Pattern SCHEMA_VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)}");

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
    @DisplayName("Переменные схемы и ключи services.app.environment в docker-compose.yaml совпадают один в один")
    void everySchemaVariableIsDeclaredInDockerCompose() throws Exception {
        Properties schema = loadSchema();

        Set<String> schemaVariables = schema.stringPropertyNames().stream().map(schema::getProperty)
                .flatMap(value -> variables(value).stream()).collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(composeAppEnvironment()).containsExactlyInAnyOrderElementsOf(schemaVariables);
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
            return new LinkedHashSet<>(environment.keySet());
        }
    }
}
