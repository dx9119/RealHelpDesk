-- Индекс для очистки jwt_tokens по времени создания (ротация refresh-токенов).
CREATE INDEX IF NOT EXISTS idx_jwt_tokens_created_at ON jwt_tokens (created_at);
