-- Only loaded by FarmCropTransactionTests into its private H2 in-memory database.
CREATE TABLE `user` (
    user_id BIGINT PRIMARY KEY
);

CREATE TABLE user_crop (
    crop_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES `user`(user_id),
    crop_name VARCHAR(100) NOT NULL,
    stage VARCHAR(100),
    province VARCHAR(100),
    city VARCHAR(100),
    region VARCHAR(100),
    location_id VARCHAR(100),
    sowing_date DATE,
    transplant_date DATE,
    stage_mode VARCHAR(20),
    estimated_stage VARCHAR(100),
    stage_confidence DOUBLE,
    stage_reason VARCHAR(1000),
    stage_evaluated_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
