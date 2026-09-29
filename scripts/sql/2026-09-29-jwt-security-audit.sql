-- Миграция к аудиту JWT (ветка audit/jwt-security)
-- СУБД: PostgreSQL. Выполняется вручную для сред с spring.jpa.hibernate.ddl-auto=none.
-- В docker-compose ddl-auto=create-drop — схема создаётся Hibernate из entity, скрипт не нужен.

-- 1) Отзыв access-токенов: версия токенов пользователя (claim "ver" в access-JWT).
--    Инкремент происходит при логауте и при смене пароля.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

-- 2) Refresh-токены храним в виде SHA-256 hex-хеша (64 символа).
--    Существующие строки хешируем тем же алгоритмом, что и приложение.
UPDATE jwt_tokens
SET token_refresh = encode(sha256(convert_to(token_refresh, 'UTF8')), 'hex')
WHERE length(token_refresh) <> 64;

ALTER TABLE jwt_tokens
    ALTER COLUMN token_refresh TYPE varchar(64);

-- Быстрый поиск refresh-токена по хешу (JPA: @Column(unique = true))
CREATE UNIQUE INDEX IF NOT EXISTS uk_jwt_tokens_token_refresh
    ON jwt_tokens (token_refresh);
