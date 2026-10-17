-- Источник учётной записи: добавляется KEYCLOAK (вход через Keycloak).
-- CHECK пересоздаётся: изменить набор допустимых значений в существующем
-- ограничении PostgreSQL не умеет — нужен DROP + ADD.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_platform_source_check;

ALTER TABLE users
    ADD CONSTRAINT users_user_platform_source_check
    CHECK (user_platform_source IN ('LOCAL', 'TELEGRAM', 'KEYCLOAK'));
