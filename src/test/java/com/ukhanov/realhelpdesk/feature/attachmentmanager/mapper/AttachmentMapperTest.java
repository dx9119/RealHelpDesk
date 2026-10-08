package com.ukhanov.realhelpdesk.feature.attachmentmanager.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.domain.attachment.model.AttachmentModel;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Маппер вложений: downloadUrl и toResponse")
class AttachmentMapperTest {

    private static final Long PORTAL_ID = 5L;
    private static final Long TICKET_ID = 77L;
    private static final Long ATTACHMENT_ID = 15L;
    private static final String PATH = "/api/v1/portals/5/tickets/77/attachments/15";

    @Test
    @DisplayName("static.base-url не задан — downloadUrl относительный, файлы отдаёт API, как до настройки")
    void downloadUrl_withoutBaseIsRelative() {
        AttachmentMapper mapper = new AttachmentMapper(new StaticProperties());

        assertThat(mapper.downloadUrl(PORTAL_ID, TICKET_ID, ATTACHMENT_ID)).isEqualTo(PATH);
    }

    @Test
    @DisplayName("static.base-url задан — downloadUrl абсолютный, путь API не меняется")
    void downloadUrl_withBaseIsAbsolute() {
        StaticProperties properties = new StaticProperties();
        properties.setBaseUrl("https://cdn.example.com");
        AttachmentMapper mapper = new AttachmentMapper(properties);

        assertThat(mapper.downloadUrl(PORTAL_ID, TICKET_ID, ATTACHMENT_ID)).isEqualTo("https://cdn.example.com" + PATH);
    }

    @Test
    @DisplayName("Хвостовой слэш в base-url не даёт двойного слэша в URL")
    void downloadUrl_stripsTrailingSlash() {
        StaticProperties properties = new StaticProperties();
        properties.setBaseUrl("http://10.0.0.5:9100/");
        AttachmentMapper mapper = new AttachmentMapper(properties);

        assertThat(mapper.downloadUrl(PORTAL_ID, TICKET_ID, ATTACHMENT_ID)).isEqualTo("http://10.0.0.5:9100" + PATH);
    }

    @Test
    @DisplayName("toResponse переносит downloadUrl в ответ API")
    void toResponse_carriesDownloadUrl() {
        StaticProperties properties = new StaticProperties();
        properties.setBaseUrl("https://cdn.example.com");
        AttachmentMapper mapper = new AttachmentMapper(properties);
        AttachmentModel attachment = new AttachmentModel();
        attachment.setId(ATTACHMENT_ID);
        attachment.setFileName("report.pdf");

        AttachmentResponse response = mapper.toResponse(attachment, PORTAL_ID, TICKET_ID);

        assertThat(response.getDownloadUrl()).isEqualTo("https://cdn.example.com" + PATH);
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getUploadedByFullName()).isEqualTo("Неизвестный автор");
    }

    @Test
    @DisplayName("Конструктор без настройки статики — ошибка, а не NPE в момент сборки URL")
    void constructor_rejectsNullProperties() {
        assertThatThrownBy(() -> new AttachmentMapper(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("staticProperties");
    }
}
