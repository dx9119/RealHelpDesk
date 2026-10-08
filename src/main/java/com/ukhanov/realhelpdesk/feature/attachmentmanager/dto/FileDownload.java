package com.ukhanov.realhelpdesk.feature.attachmentmanager.dto;

import java.io.InputStream;

import com.ukhanov.realhelpdesk.core.http.RangeHeader;

/**
 * Всё, что нужно контроллеру, чтобы отдать файл: метаданные для заголовков, разрешённый диапазон и открытый поток из хранилища.
 *
 * <p>
 * Владение потоком — на контроллере: он закрывается в try-with-resources после записи в ответ. Если диапазон неудовлетворим (416), поток не
 * открывается — {@code content} равен null, соединение с хранилищем не устанавливается.
 * </p>
 */
public record FileDownload(String fileName, String contentType, long sizeBytes, RangeHeader.Parsed range, InputStream content) {

    /** Диапазон за пределами файла: контроллер отвечает 416, потока нет. */
    public boolean isUnsatisfiable() {
        return content == null;
    }

    /** Отдаётся фрагмент (206): в заголовках нужен Content-Range, Content-Length — длина фрагмента. */
    public boolean isPartial() {
        return range.kind() == RangeHeader.Kind.PARTIAL;
    }
}
