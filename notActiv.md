# Отчёт: мёртвый код и код совместимости (RealHelpDesk)

Анализ: 199 main-файлов, 56 тестов. Baseline `mvn -o -DskipTests compile` — успешно.
Ниже только подтверждённые находки (проверены grep'ом по `src`, аннотациям Spring/JPA/Jackson и графу вызовов).

## A. Мёртвый код (нет активных ссылок)

| # | Путь к файлу | Метод/класс | Обоснование |
|---|---|---|---|
| 1 | `src/main/java/com/ukhanov/realhelpdesk/core/mail/repository/EmailEventStats.java` | `EmailEventStats` (весь интерфейс) | Spring Data-проекция, 0 ссылок; `EmailLogRepository` её не возвращает. |
| 2 | `src/main/java/com/ukhanov/realhelpdesk/core/mail/dto/TicketChangeDto.java` | `TicketChangeDto` | DTO нигде не создаётся/не сериализуется — 0 ссылок. |
| 3 | `src/main/java/com/ukhanov/realhelpdesk/core/security/auth/logout/dto/LogoutResponse.java` | `LogoutResponse` | DTO не используется; `AuthController.logout` возвращает `ResponseEntity<Void>`. Подтверждено `docs/jwt-audit.md:50`. |
| 4 | `src/main/java/com/ukhanov/realhelpdesk/core/security/auth/login/exception/LoginException.java` | `LoginException` | Не бросается и не ловится; `GlobalExceptionHandler` работает с базовым `ApiException`. |
| 5 | `src/main/java/com/ukhanov/realhelpdesk/core/config/WhiteUrlConfig.java:16` | `isMyTelegramBot(HttpServletRequest)` | Мёртвый legacy Telegram-метод (сравнение заголовка с пустой строкой). `WHITE_LIST_URLS` остаётся. |
| 6 | `src/main/java/com/ukhanov/realhelpdesk/core/security/user/service/CustomUserDetailsService.java:24` | `loadUserById(Long)` | 0 вызовов; не метод интерфейса `UserDetailsService` (каркасный — только `loadUserByUsername`). |
| 7 | `src/main/java/com/ukhanov/realhelpdesk/core/security/user/SecurityUser.java:44` | `getLatestJwtRefreshToken()` | 0 вызовов; остаток прежнего дизайна refresh-токенов. |
| 8 | `src/main/java/com/ukhanov/realhelpdesk/core/security/accesscontrol/AccessValidationService.java:20-24` | параметр `TicketDomainService ticketDomainService` | Не присваивается и не используется в теле (лишняя связь доменов). Правка одного теста. |
| 9 | `src/main/java/com/ukhanov/realhelpdesk/core/security/auth/tokens/service/ChangeTokenService.java:23-28` | параметр `GetTokenService getTokenService` | Не сохраняется и не используется; мёртвая инъекция. |
| 10 | `src/main/java/com/ukhanov/realhelpdesk/domain/portal/service/PortalHistoryService.java:55` | `static String valueOf(Object)` | 0 вызовов, дублирует `String.valueOf`. |
| 11 | `src/main/java/com/ukhanov/realhelpdesk/domain/notification/repository/UserNotificationPreferencesRepository.java:19` | `deleteByUserId(Long)` | Derived-delete метод без вызовов (автоматически не исполняется). |
| 12 | `src/main/java/com/ukhanov/realhelpdesk/domain/portal/service/PortalDomainService.java:66` | `deletePortalById(Long)` | Боевой путь — soft-delete; hard-delete метод остался только в `PortalDomainServiceTest`. |
| 13 | `src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:128` | `getAllPortals()` | 0 вызовов. |
| 14 | `src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:134` | `getAllPortalIds()` | 0 вызовов. |
| 15 | `src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalManageService.java:328` | `getUserActivityInPublicPortals()` | 0 вызовов; единственный вызывающий для п.16. |
| 16 | `src/main/java/com/ukhanov/realhelpdesk/domain/portal/service/PortalDomainService.java:79` | `getPublicPortalsByUserActivity(Long)` | 0 боевых вызовов после удаления п.15 (транзитивный мёртвый). |
| 17 | `src/main/java/com/ukhanov/realhelpdesk/feature/portalmanager/service/PortalUtilsService.java:17` | `isValidUserId(String)` | Только тест `PortalUtilsServiceTest`; производство не использует. |
| 18 | `src/main/java/com/ukhanov/realhelpdesk/feature/usermanager/mapper/UserMapper.java:34` | `toModel(UserInfoRequest)` | 0 вызовов; обновление пользователя копирует поля инлайн. |
| 19 | `src/main/java/com/ukhanov/realhelpdesk/core/mail/model/EmailTemplates.java:70,74` | `portalAddUserSubject/portalAddUserBody` | Только тест; письмо «добавлен к порталу» производством не отправляется. |
| 20 | `EmailPolicyService`, `PaginationAdapter`, `DecodeTokenService`, `GenTokenService`, `SaveTokenService`, `CurrentUserProvider`, `MessageDomainService` | `private static final Logger logger` | Объявлено, но `logger.` не вызывается. Удалить поле + импорты. |
| 21 | `feature/portalmanager/mapper/PortalMapper.java:12`, `feature/usermanager/mapper/UserMapper.java:11` | `@Component` у статических утилит | Бины нигде не инжектятся, используются только статические вызовы → мёртвая регистрация. |
| 22 | `TicketPriorityRequest`, `TicketStatusRequest`, `PortalUsersRequest`, `PortalVisibilityRequest` | 1-арг конструкторы | 0 вызовов `new`; Jackson использует no-arg. |
| 23 | `UserManageException`, `MessageException`, `AttachmentException`, `NotificationException` | неиспользуемые перегрузки конструкторов | Вызываются лишь отдельные сигнатуры (остальные — 0). |
| 24 | `pom.xml:59` | зависимость `spring-boot-starter-oauth2-resource-server` | В коде нет ни одной ссылки на OAuth2/ResourceServer/JwtDecoder. |
| 25 | `MessageManageService.getAllMessage`, `AttachmentManageService.getAttachments/uploadAttachment`, `AttachmentController.uploadAttachment` | лишние `throws` | Объявленные исключения не бросаются (`MessageException`, `PortalException` и др.). |

## B. Код совместимости (поддержка старой и новой логики одновременно)

| # | Путь к файлу | Метод/класс | Обоснование |
|---|---|---|---|
| C1 | `core/pagination/service/PaginationAdapter.java:30-32` | `<T> PageResponse<T> mapToResponse(Page<T>, String sortBy, String order)` | Перегрузка игнорирует `sortBy`/`order` и делегирует в 1-арг метод. Параметры сохранены только ради 7 старых вызовов (`TicketManageService`×3, `PortalManageService`×3, `NotificationManageService`×1). |
| C2 | `feature/ticketmanager/dto/TicketResponseOld.java` + `feature/ticketmanager/mapper/TicketMapper.java:32` | `TicketResponseOld` / `TicketMapper.toResponse` | Параллельная legacy-модель ответа (10 полей, Builder) рядом с новым `TicketResponse` (6 полей). Старый DTO отдают 4 эндпоинта: `GET /portals/{id}/tickets`, `/tickets/{id}`, `/tickets/ids`, `/tickets/mine`. **API-видимое изменение.** |
| C3 | `feature/ticketmanager/controller/TicketSearchController.java:57-62` | `getMyTickets` → `GET /tickets/mine` | Дублирует `GET /tickets?mine=true` (новый `TicketSearchService`+`TicketResponse`). Legacy-путь тянет `TicketManageService.getPageTicketsByAutor`. |
| C4 | `feature/ticketmanager/service/TicketManageService.java` | `getPageTicketsByAutor` / `getPageTickets` / `getPageTicketsByIds` | Старый пагинированный путь (домен + `PaginationAdapter` + `TicketResponseOld`) параллельно новому `TicketSearchService` (JPA `Specification`). Распадается при выполнении C2/C3. |
| C5 | `feature/portalmanager/service/PortalManageService.java:128,134` | `getAllPortals` / `getAllPortalIds` | Legacy-методы «выгрузить всё» рядом с пагинированными `getPagePortalsByOwner`/`mapAccessiblePortalsToIds`; без вызовов. |
| C6 | `PortalManageService.resolveUserName:271`, `PortalOwnerTransferService.displayName:453`, `TicketMapper.java:35-37` | дублирующие хелперы ФИО | Три копии `lastName + " " + firstName` с fallback для удалённого пользователя — свести к одной утилите. |

## C. Требуют решения (не удалялись)

- `feature/notificationmanager/service/NotificationWaitRegistry.java:63` `waiterCount(Long)` — только тест `NotificationWaitRegistryTest`.
- `feature/notificationmanager/dto/NotificationPreferencesRequest.java` — конструкторы `(Set)` и `(Set,Boolean,Integer)` используются только тестами.
- П. C2/C3/C4 затрагивают контракт REST API (`TicketResponseOld` содержит `body`, `priority`, `status`, `accessStatus`, которых нет в `TicketResponse`). Нужно решить, какой DTO остаётся эталонным.
