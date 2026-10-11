package com.ukhanov.realhelpdesk.core.pagination.dto;

import java.util.Collection;
import java.util.List;

/**
 * Обёртка для коллекций, у которых нет постраничности (id-наборы, полные списки сообщений и вложений).
 *
 * <p>
 * Массив на верхнем уровне JSON не расширяем: добавить {@code total} или метаданные можно только ломающим изменением. Объект с полем
 * {@code items} оставляет место для роста и выравнивает стиль ответов (объект вместо массива) по всему API.
 */
public record ItemsResponse<T>(List<T> items, long total) {

    public static <T> ItemsResponse<T> of(Collection<T> items) {
        List<T> copy = List.copyOf(items);
        return new ItemsResponse<>(copy, copy.size());
    }
}
