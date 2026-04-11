-- Run once on an existing leafquery database.

CREATE TABLE user_crop (
    crop_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    crop_name VARCHAR(64) NOT NULL,
    stage VARCHAR(64) DEFAULT '',
    province VARCHAR(64) DEFAULT '',
    city VARCHAR(64) DEFAULT '',
    region VARCHAR(64) DEFAULT '',
    location_id VARCHAR(32) DEFAULT '',
    sowing_date DATE NULL,
    transplant_date DATE NULL,
    stage_mode VARCHAR(16) DEFAULT 'MANUAL',
    estimated_stage VARCHAR(64) DEFAULT '',
    stage_confidence DECIMAL(4,2) DEFAULT 0,
    stage_reason VARCHAR(255) DEFAULT '',
    stage_evaluated_at TIMESTAMP NULL,
    is_active TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crop_user_id (user_id),
    INDEX idx_crop_user_active (user_id, is_active),
    CONSTRAINT fk_crop_user FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE
);

ALTER TABLE identification_record
    ADD COLUMN crop_id BIGINT NULL AFTER user_id,
    ADD COLUMN crop_name VARCHAR(64) DEFAULT '' AFTER crop_id,
    ADD COLUMN location_id VARCHAR(32) DEFAULT '' AFTER confidence,
    ADD COLUMN city VARCHAR(64) DEFAULT '' AFTER location_id,
    ADD COLUMN region VARCHAR(64) DEFAULT '' AFTER city;

ALTER TABLE identification_record
    ADD INDEX idx_record_crop_id (crop_id);

ALTER TABLE identification_record
    ADD CONSTRAINT fk_record_crop
        FOREIGN KEY (crop_id) REFERENCES user_crop(crop_id) ON DELETE SET NULL;
