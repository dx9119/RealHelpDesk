# Code Style — RealHelpDesk

Единые правила стиля кода для всего проекта. Правила закреплены инструментами и проверяются автоматически при каждой сборке.

## Инструменты

| Инструмент | Что делает | Где задан |
|---|---|---|
| EditorConfig | отступы, кодировка, переводы строк — для всех IDE | `.editorconfig` |
| Spotless (Eclipse JDT) | автоформатирование Java, порядок и чистка импортов | `pom.xml`, `config/eclipse/eclipse-format.xml` |
| Checkstyle | нейминг, импорты, дизайн, длина строк | `pom.xml`, `config/checkstyle/checkstyle.xml` |

```bash
mvn spotless:apply    # применить форматирование
mvn spotless:check    # проверить форматирование
mvn checkstyle:check  # проверить правила стиля
mvn validate          # обе проверки + компиляция (запускаются и на mvn test/package)
```

Конфиги подключены к фазе `validate` — сборка падает при нарушении стиля.

## Настройка IntelliJ IDEA

Форматтер Spotless (Eclipse JDT) — это библиотека внутри Maven-плагина, сама IDE Eclipse не нужна.
Чтобы IDEA форматировала код так же, как `mvn spotless:apply`:

1. `.editorconfig` IDEA читает автоматически (отступы, кодировка, LF, финальный `\n`) — ничего делать не нужно.
2. Импортируйте профиль форматирования:
   `Settings → Editor → Code Style → Java → ⚙ → Import Eclipse XML Profile…` →
   выбрать `config/eclipse/eclipse-format.xml`.
3. Опционально (точнее встроенный импорт): плагин **Eclipse Code Formatter** (`plugins.jetbrains.com/plugin/7104`),
   в его настройках указать тот же `config/eclipse/eclipse-format.xml` и включить форматирование при сохранении.
4. После импорта: `Settings → Editor → Code Style → Java → ⚙ → Override default color or font settings`
   не требуется — убедитесь, что выбран импортированный scheme (он становится активным автоматически).

Перед коммитом проверьте себя: `mvn spotless:check` — если файлы «грязные», IDEA форматирует иначе,
либо переимпортируйте профиль, либо выполните `mvn spotless:apply`.


## Форматирование

- **Отступы:** 4 пробела, табуляции запрещены. Продолжения выражений — 8 пробелов (2 уровня).
- **Длина строки:** максимум 140 символов (длинные литералы разбивать конкатенацией).
- **Кодировка/переводы:** UTF-8, LF, файл заканчивается переводом строки, без хвостовых пробелов.
- **Скобки:** всегда `if (a) { ... }` — одиночные statement без `{}` запрещены.
- **Порядок членов класса:** `static final` поля → instance-поля → конструктор → public-методы.

## Импорты

Группы в таком порядке, разделённые пустой строкой, внутри группы — по алфавиту:

```java
import java.util.List;          // java
                                // (пустая строка)
import jakarta.validation.*;    // jakarta
                                // (пустая строка)
import org.slf4j.Logger;        // org
                                // (пустая строка)
import com.ukhanov...;          // com
                                // (пустая строка)
import static ...;               // static — всегда в конце
```

- **Wildcard-импорты (`import pkg.*;`) запрещены** — только явные имена.
- Статические импорты — только в тестах.
- Неиспользуемые и дублирующие импорты удаляет Spotless автоматически.

## Именование

| Элемент | Правило | Пример |
|---|---|---|
| Пакеты | `lowercase`, без `_`, единственное число | `feature.ticketmanager.service` |
| Классы | `PascalCase` + суффикс роли | `TicketManageService`, `CreateTicketRequest` |
| Методы | `camelCase`, глагол | `getPageTickets` |
| Тест-методы | `method_condition_outcome` (через `_`) | `getTicketById_nullId_throwsNPE` |
| Поля/переменные/параметры | `camelCase` | `currentUserProvider` |
| Константы | `UPPER_SNAKE_CASE` | `SORTABLE_TICKET_FIELDS` |
| Логгер | строчный `logger` (исключение в Checkstyle) | `private static final Logger logger` |

Суффиксы ролей: `*Service`, `*Controller`, `*Repository`, `*Mapper`, `*ExceptionHandler`, `*Config`, `*Request`, `*Response`, `*Exception`, `*Model` (JPA entity).

## Spring / Java-конвенции

- **Java 21**, `jakarta.*` (не `javax.*`), Lombok не используется.
- **Constructor injection** — только через конструктор с `private final`-полями, `@Autowired` запрещён.
- Утилитарные классы (только static-методы): `final class` + приватный конструктор.
- DTO — обычные классы с геттерами/сеттерами (без Lombok); `record` — для immutable value-объектов.
- `@Transactional` — только на методах, кроме `readOnly`-поисковых сервисов.
- Аннотации — с отдельной строки, порядок: stereotype → mapping/транзакция.

## Null-безопасность

- Границы доверия: `Objects.requireNonNull(value, "русское сообщение")`.
- В репозиториях — `Optional` + `orElseThrow(...)` с логированием.
- `@Nullable`/`@NonNull` не используются — контракт описывается requireNonNull и `Optional`.

## Исключения

- Одно checked-исключение `*Exception extends Exception` на подсистему, конструкторы `(String)` и `(String, Throwable)`.
- Проброс через `throws` до контроллера, ловится `@ControllerAdvice`-обработчиком (`*ExceptionHandler` с `@Order`).
- Ответ обработчика: `ResponseEntity<Map<String, String>>`, русские ключи/сообщения, HTTP 409 для бизнес-ошибок.

## Логирование и комментарии

- SLF4J: `private static final Logger logger = LoggerFactory.getLogger(X.class);`
- Уровни привязаны к профилям `debug` и `prod` (`application-debug.properties`, `application-prod.properties`):
  - `ERROR` — неожиданный сбой, со стеком. Без токенов, паролей, кодов восстановления и `toString()` сущностей.
  - `WARN` — отказ во входе, лимит запросов, отклонённый токен, конфликт версий, проглоченная ошибка.
  - `INFO` — завершённое бизнес-событие (создан, изменён, удалён, зарегистрирован, письмо отправлено) и ответ HTTP 4xx. Видно в `prod`.
  - `DEBUG` — трассировка шагов и успешные HTTP-запросы. Видно только в `debug`.
- Не дублировать «метод вызван» в сервисе: строку запроса пишет `LoggingFilter`. Query-string не логировать.
- Сообщения логов, тексты ошибок, `@DisplayName` — **по-русски**; имена кода, URL, ключи JSON — **по-английски**.
- Javadoc в `src/main` не обязателел; `//`-комментарии — только для «почему», не «что». `TODO`/`FIXME` не использовать (найти задачу).

## Тесты

- **JUnit 5 + Mockito + AssertJ**, `@SpringBootTest` не используется (изолированные unit-тесты и standalone-MockMvc).
- Класс: `<ТестируемыйКлас>Test`, package-private (`class X`, без `public`), зеркалит пакет main.
- Метод: `method_condition_outcome` на английском — `searchTickets_noAccessiblePortals_returnsEmptyPage`.
- Аннотации `@Mock`/`@InjectMocks` — каждая на своей строке; заглушки `mock*`, captor'ы `*Captor`.
- Секции `// given` / `// when` / `// then` — где логика неочевидна; `@DisplayName` — по-русски.
- Строгость Mockito: `@MockitoSettings(strictness = Strictness.LENIENT)` допустима только вместе с явным `lenient().when(...)`.

## Коммиты

Conventional Commits с русским описанием:

```
fix(auth): убрать fallback на сырой refresh-токен в поиске
test(model): проверить выставление createdAt у всех сущностей
style(core): привести импорты к единому порядку
```

Типы: `fix`, `feat`, `refactor`, `test`, `docs`, `style`, `chore`, `build`.
