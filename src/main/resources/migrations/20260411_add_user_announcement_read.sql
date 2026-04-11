-- Run once on an existing leafquery database.

CREATE TABLE IF NOT EXISTS user_announcement_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_user_announcement (user_id, announcement_id),
    KEY idx_announcement_read_user (user_id),
    KEY idx_announcement_read_announcement (announcement_id),
    CONSTRAINT fk_user_announcement_read_user
        FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_announcement_read_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcement(id) ON DELETE CASCADE
);
