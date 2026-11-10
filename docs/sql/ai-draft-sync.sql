-- Apply before starting a backend with draft synchronization. Existing rows retain NULL keys.
BEGIN;
ALTER TABLE ai_conversations ADD COLUMN IF NOT EXISTS client_key VARCHAR(128);
ALTER TABLE ai_messages ADD COLUMN IF NOT EXISTS client_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_conversation_client_key ON ai_conversations(user_id, client_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_message_client_key ON ai_messages(conversation_id, client_key);
COMMIT;
