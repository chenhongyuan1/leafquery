package com.example.leafquery.service;

import com.example.leafquery.dto.PredictionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 先选后扫架构的路由判断服务。
 * 职责：
 * 1. 根据用户预选类别判断是否需要跑 YOLO
 * 2. 根据 YOLO 结果判断是否需要 Vision LLM 复核
 */
@Service
public class DetectionSelectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionSelectionService.class);

    /**
     * YOLO 模型支持的用户类别。
     * 选了这些中任意一项 → 跑 YOLO；
     * 只选了"其他" → 跳过 YOLO，直接 Vision LLM。
     */
    private static final Set<String> YOLO_SUPPORTED = Set.of("水稻", "玉米", "小麦", "虫害");

    /**
     * 判断用户选择的类别是否需要跑 YOLO。
     */
    public boolean needsYolo(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        return categories.stream().anyMatch(YOLO_SUPPORTED::contains);
    }

    /**
     * 判断是否需要 Vision LLM 复核。
     *
     * @param prediction YOLO 检测结果（跳过 YOLO 时为 null）
     * @param yoloUsed   是否实际使用了 YOLO
     */
    public boolean isReviewRequired(PredictionResult prediction, boolean yoloUsed) {
        // 跳过 YOLO → 必须走 Vision LLM 复核
        if (!yoloUsed) {
            return true;
        }
        // YOLO 返回空 → 需要复核
        if (prediction == null) {
            return true;
        }
        String scene = prediction.getSceneType();
        // uncertain（低置信）或 empty（未检出） → 需要复核
        // multi 不触发复核！只要主目标置信度 ≥ 0.60 就走简单路径
        return "uncertain".equals(scene) || "empty".equals(scene);
    }

    /**
     * 清理和去重用户输入的名称列表。
     */
    public List<String> sanitizeCategories(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
