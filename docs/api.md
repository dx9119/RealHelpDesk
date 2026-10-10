# API

Базовый URL — `/api/v1`. Пагинированные ответы имеют вид `PageResponse`:
`{ content, page, size, totalElements, totalPages, last }`.

## Служебные

| Метод и путь | Назначение |
|---|---|
| `GET /health` | Проверка живости |
| `GET /captcha?capId=` | PNG-капча; `capId` — идентификатор посетителя (≤ 10 символов) |

## Аутентификация (`/auth`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /register` | `RegisterRequest` + опц. `?capId=` | Регистрация; ставит cookies |
| `POST /login` | `{ email, password }` | Вход; ставит cookies |
| `POST /tokens/access` | refresh-cookie | Новый access-токен и ротация refresh |
| `GET /tokens/refresh` | — | Статус refresh-токена (`{ tokenStatus, createdAt }`) |
| `GET /session` | — | Текущая авторизация (`{ authorization }`) |
| `DELETE /session` | — | Логаут, отзыв токенов |

`RegisterRequest`: `firstName`, `lastName`, `email`, `password`, опц. `capCode`
(при включённой капче), `externalId`, `userPlatformSource`.

## Пользователи (`/users`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `GET /profile` | — | Профиль (`UserInfoResponse`) |
| `PUT /profile` | `{ firstName, lastName, middleName, additionalInfo }` | Обновление профиля |
| `POST /password-resets` | `{ email }` | Запрос сброса пароля (письмо с кодом) |
| `PUT /password-resets/{code}` | `{ password }` | Установка нового пароля |

## Email (`/email`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /confirmations/{token}` | — | Подтверждение email по коду |
| `GET /info` | — | Текущий уровень email-оповещений (`{ muteLevel }`) |
| `DELETE /notifications/{event}` | — | Отписка от события (`NotificationEvent`) |
| `POST /codes?capId=` | опц. `capId` | Отправка кода подтверждения email |

## Порталы (`/portals`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /` | `{ name, description }` | Создание портала → `{ id }` |
| `GET /` | `page, size, sortBy, order` | Порталы владельца |
| `GET /shared` | `page, size, sortBy, order` | Доступные общие порталы |
| `GET /ids` | — | ID доступных порталов |
| `GET /info` | — | Краткая информация по доступным порталам |
| `GET /{portalId}` | — | Информация о портале |
| `PUT /{portalId}` | `{ name, description }` | Переименование/описание |
| `GET /shared/{portalId}` | — | Настройки: участники + `isPublic` |
| `PUT /shared/{portalId}/users` | `{ userIds: [...] }` | Замена списка участников |
| `GET /shared/{portalId}/visibility` | — | Текущая публичность |
| `PUT /shared/{portalId}/visibility` | `{ isPublic }` | Смена публичности |
| `DELETE /?ids=1,2` | `ids` | Удаление порталов → `{ count, deletedIds }` |

`PortalModel`-контракт: `PortalResponse` (`id, name, description, createdAt`),
`PortalInfoResponse` (`id, name, description`), `PortalSettingsResponse`
(`users: [{id, firstName, lastName, middleName, email}], isPublic`).

## Передача владения и история (`/portals/{id}/...`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `POST /{id}/owner-transfer` | `{ email, password, reason, keepOldOwnerAsMember }` | Инициировать передачу |
| `GET /{id}/owner-transfer` | — | Активный запрос (владелец или предлагаемый) |
| `POST /{id}/owner-transfer/confirm` | `{ password, reason }` | Подтвердить передачу |
| `POST /{id}/owner-transfer/reject` | `{ reason }` | Отклонить |
| `POST /{id}/owner-transfer/cancel` | — | Отозвать до решения |
| `GET /{id}/history?page=&size=` | — | История портала (`PageResponse<PortalHistoryResponse>`) |

Активный запрос живёт 72 часа и закрывается лениво как `EXPIRED`; одновременно
на портал может быть только один запрос (второй — `409`). Статусы:
`PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`. При подтверждении
владелец меняется, новый владелец убирается из участников, старый при
`keepOldOwnerAsMember=true` остаётся участником.

История (`portal_history`) хранит события передачи (`TRANSFER_*`) и изменения
портала (`NAME_CHANGED`, `DESCRIPTION_CHANGED`, `VISIBILITY_CHANGED`,
`USERS_CHANGED`, `PORTAL_DELETED`); доступ — участникам портала.

## Заявки

Портальные эндпоинты (`/portals/{portalId}/tickets`):

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST /portals/{portalId}/tickets` | `{ title, body, ticketPriority, ticketAccessStatus }` | Создание заявки → `{ id }` |
| `GET /portals/{portalId}/tickets` | `status, page, size, sortBy, order` | Заявки портала |
| `GET /portals/{portalId}/tickets/ids` | `status, noAnswer` | ID заявок (фильтры) |
| `GET /portals/{portalId}/tickets/{ticketId}` | — | Заявка по ID |
| `PUT /portals/{portalId}/tickets/{ticketId}/status` | `{ status }` | Смена статуса |
| `PUT /portals/{portalId}/tickets/{ticketId}/priority` | `{ priority }` | Смена приоритета |
| `DELETE /portals/{portalId}/tickets/{ticketId}` | — | Удаление |

Поиск (`/tickets`):

| Метод и путь | Параметры | Назначение |
|---|---|---|
| `GET /tickets` | `search`, `startDate`, `endDate`, `status`, `priority`, `mine`, `page, size, sort` | Поиск заявок (`TicketResponse`) |
| `GET /tickets/mine` | `page, size, sortBy, order` | Мои заявки (`TicketResponseOld`) |

`TicketResponse`: `id, title, authorFullName, portalName, createdAt, portalId`.
`TicketResponseOld` дополнительно содержит `body`, `ticketPriority`,
`ticketStatus`, `ticketAccessStatus`.

`TicketPriority`: `CRITICAL, HIGH, MEDIUM, LOW, NONE`.
`TicketStatus`: `OPEN, IN_PROGRESS, CLOSED`.
`TicketAccessStatus`: `ALL_USERS, CREATOR_AND_PORTAL_USERS`.

## Сообщения (`/portals/{portalId}/tickets/{ticketId}/messages`)

| Метод и путь | Тело | Назначение |
|---|---|---|
| `POST .../messages` | `{ messageText }` | Создать сообщение → `{ id }` |
| `GET .../messages` | — | Сообщения заявки (`List<MessageResponse>`) |

## Вложения (`/portals/{portalId}/tickets/{ticketId}/attachments`)

| Метод и путь | Тело / параметры | Назначение |
|---|---|---|
| `POST .../attachments` | `multipart/form-data`: `file` + опц. `messageText` | Загрузка вложения |
| `GET .../attachments` | — | Список вложений |
| `GET .../attachments/{attachmentId}` | заголовок `Range` | Скачивание (поток с Range) |

`AttachmentResponse`: `id, messageId, ticketId, fileName, contentType,
sizeBytes, uploadedByFullName, createdAt, downloadUrl`.

## Оповещения (`/notifications`)

| Метод и путь | Параметры / тело | Назначение |
|---|---|---|
| `GET /notifications` | `page, size, unreadOnly` | Список оповещений |
| `GET /notifications/unread-count` | — | Счётчик непрочитанных `{ count }` |
| `GET /notifications/wait` | `afterId, timeoutSec, size` | Long polling (лимит `notifications-wait`) |
| `PUT /notifications/{id}/read` | — | Прочитано (гасит группу повторов) |
| `PUT /notifications/read-all` | — | Прочитать всё |
| `GET /notifications/preferences` | — | Включённые события и повторы |
| `PUT /notifications/preferences` | `{ events, repeatEnabled, repeatIntervalMinutes }` | Настройка событий/повтора |

Получатели — владелец портала и участники (`allowedUserIds`); автор действия
уведомление о своём действии не получает. `NEW_TICKET` и `NEW_MESSAGE`
повторяются, пока не прочитаны (по умолчанию каждые 30 минут). `wait`
возвращает страницу оповещений новее `afterId` либо пустую по таймауту
(по умолчанию 25 c, максимум 30).

`NotificationEvent`: `NEW_TICKET, NEW_MESSAGE, NEW_SYSTEM_MESSAGE,
NEW_TICKET_OR_MESSAGE, NEW_PORTAL, CHANGE_TICKET, RECOVERY_PASSWORD,
TICKET_DELETED, PORTAL_DELETED, PORTAL_TRANSFER_*, NONE`.
