package com.ukhanov.realhelpdesk.core.mail.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.PropertyPlaceholderHelper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Конфигурация email: EmailProperties")
class EmailPropertiesTest {

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
    @DisplayName("email.domain и email.project-name заданы в application.properties (значения больше не захардкожены в коде)")
    void domainAndProjectNameDeclaredInApplicationProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(stream).as("application.properties в classpath").isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, false);

        assertThat(helper.replacePlaceholders(properties.getProperty("email.domain"), properties::getProperty))
                .isEqualTo("front.example.ru");
        assertThat(helper.replacePlaceholders(properties.getProperty("email.project-name"), properties::getProperty))
                .isEqualTo("real help desk");
    }
}
