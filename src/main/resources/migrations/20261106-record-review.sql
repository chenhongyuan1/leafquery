-- Existing databases: apply once before deploying the matching application version.
-- Nullable columns preserve the unknown review state of historical records.
ALTER TABLE identification_record
    ADD COLUMN yolo_used BOOLEAN NULL COMMENT '识别时是否使用YOLO，NULL为历史未知';
ALTER TABLE identification_record
    ADD COLUMN review_required BOOLEAN NULL COMMENT '是否需要AI视觉复核，NULL为历史未知';
ALTER TABLE identification_record
    ADD COLUMN review_result LONGTEXT NULL COMMENT '识别时的AI视觉复核结果快照';
ALTER TABLE identification_record
    ADD COLUMN review_error TEXT NULL COMMENT '识别时的AI视觉复核失败原因';
