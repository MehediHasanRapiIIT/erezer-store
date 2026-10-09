-- Replies to support messages, sent from the admin panel.
--
-- "Reply via email" only opened the mail program on the staff member's own
-- computer: the answer went from whatever mailbox was there, and nothing of it
-- was kept. A reply is now written in the admin panel, sent from the shop's own
-- address, and kept here under the message it answers, with who sent it and when.

CREATE TABLE IF NOT EXISTS contact_reply (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by BIGINT,
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    updated_at TIMESTAMP,
    updated_by BIGINT,
    version BIGINT DEFAULT 0,
    message_id UUID NOT NULL,
    body VARCHAR(4000) NOT NULL,
    -- Kept as they were when the reply was sent, so the record still reads
    -- right after a staff member is renamed or removed.
    sent_by_id UUID,
    sent_by_name VARCHAR(200),
    sent_to VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contact_reply_message ON contact_reply (message_id, id);
