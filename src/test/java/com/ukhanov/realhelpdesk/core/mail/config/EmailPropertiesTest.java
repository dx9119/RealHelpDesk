package com.ukhanov.realhelpdesk.core.mail.config;

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
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Конфигурация email: EmailProperties")
class EmailPropertiesTest {

    private static final Path APPLICATION_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yaml");
    /** Ровно ${VAR}: без дефолта после двоеточия и без литерала до/после. */
    private static final Pattern SCHEMA_VALUE = Pattern.compile("\\$\\{[A-Z_][A-Z0-9_]*}");
    private static final Pattern SCHEMA_VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)}");

    @Test
    @DisplayName("email.from и email.notify биндятся из application.properties")
    void bindsFromAndNotifyFromProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("email.from", "noreply@front.example.ru");
        environment.setProperty("email.notify", "admin@front.example.ru");

        EmailProperties properties = Binder.get(environment).bind("email", Bindable.of(EmailProperties.class)).get();

        assertThat(properties.getFrom()).isEqualTo("noreply@front.example.ru");
        assertThat(properties.getNotify()).isEqualTo("admin@front.example.ru");
    }

    @Test
    @DisplayName("Без свойств email.* биндинг не срабатывает")
    void emptyEnvironment_isNotBound() {
        BindResult<EmailProperties> result = Binder.get(new MockEnvironment()).bind("email", Bindable.of(EmailProperties.class));

        assertThat(result.isBound()).isFalse();
    }

    @Test
    @DisplayName("email.domain и email.project-name биндятся из конфига")
    void bindsDomainAndProjectNameFromProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("email.domain", "front.example.ru");
        environment.setProperty("email.project-name", "real help desk");

        EmailProperties properties = Binder.get(environment).bind("email", Bindable.of(EmailProperties.class)).get();

        assertThat(properties.getDomain()).isEqualTo("front.example.ru");
        assertThat(properties.getProjectName()).isEqualTo("real help desk");
    }

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
