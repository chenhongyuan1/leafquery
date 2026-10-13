package com.example.leafquery.service;

import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.mapper.FarmCropMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FarmCropService {

    private final FarmCropMapper farmCropMapper;
    private final AgroZoneService agroZoneService;
    private final PhenologyEstimateService phenologyEstimateService;

    public FarmCropService(FarmCropMapper farmCropMapper,
                           AgroZoneService agroZoneService,
                           PhenologyEstimateService phenologyEstimateService) {
        this.farmCropMapper = farmCropMapper;
        this.agroZoneService = agroZoneService;
        this.phenologyEstimateService = phenologyEstimateService;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<FarmCrop> getUserCrops(Long userId) {
        // This read also refreshes phenology and writes isActive through updateCrop.
        if (!lockUser(userId)) {
            return List.of();
        }
        List<FarmCrop> crops = farmCropMapper.selectByUserId(userId);
        for (FarmCrop crop : crops) {
            refreshAutoPhenologyIfNeeded(crop);
        }
        return farmCropMapper.selectByUserId(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FarmCrop createCrop(FarmCrop crop) {
        if (!lockUser(crop.getUserId())) {
            throw new IllegalArgumentException("用户不存在");
        }
        normalizeCrop(crop);
        applyPhenologyState(crop, true);
        boolean shouldActivate = Boolean.TRUE.equals(crop.getIsActive()) || farmCropMapper.countByUserId(crop.getUserId()) == 0;
        // Activate only after the insert, so activation must affect exactly one row.
        crop.setIsActive(Boolean.FALSE);

        if (shouldActivate) {
            farmCropMapper.clearActiveByUserId(crop.getUserId());
        }

        requireSingleRow(farmCropMapper.insertCrop(crop), "创建作物");
        if (shouldActivate) {
            activateCrop(crop.getCropId(), crop.getUserId());
        }
        return farmCropMapper.selectByCropId(crop.getCropId());
    }

    @Transactional(rollbackFor = Exception.class)
    public FarmCrop updateCrop(Long cropId, FarmCrop crop) {
        if (!lockUser(crop.getUserId())) {
            return null;
        }
        FarmCrop existing = farmCropMapper.selectByCropId(cropId);
        if (existing == null || crop.getUserId() == null || !crop.getUserId().equals(existing.getUserId())) {
            return null;
        }

        crop.setCropId(cropId);
        crop.setCropName(pickValue(crop.getCropName(), existing.getCropName()));
        crop.setStage(pickValue(crop.getStage(), existing.getStage()));
        crop.setProvince(pickValue(crop.getProvince(), existing.getProvince()));
        crop.setCity(pickValue(crop.getCity(), existing.getCity()));
        crop.setRegion(pickValue(crop.getRegion(), existing.getRegion()));
        crop.setLocationId(pickValue(crop.getLocationId(), existing.getLocationId()));
        crop.setSowingDate(crop.getSowingDate() == null ? existing.getSowingDate() : crop.getSowingDate());
        crop.setTransplantDate(crop.getTransplantDate() == null ? existing.getTransplantDate() : crop.getTransplantDate());
        crop.setStageMode(pickValue(crop.getStageMode(), existing.getStageMode()));
        crop.setEstimatedStage(pickValue(crop.getEstimatedStage(), existing.getEstimatedStage()));
        crop.setStageConfidence(crop.getStageConfidence() == null ? existing.getStageConfidence() : crop.getStageConfidence());
        crop.setStageReason(pickValue(crop.getStageReason(), existing.getStageReason()));
        crop.setStageEvaluatedAt(crop.getStageEvaluatedAt() == null ? existing.getStageEvaluatedAt() : crop.getStageEvaluatedAt());
        crop.setIsActive(crop.getIsActive() == null ? existing.getIsActive() : crop.getIsActive());

        normalizeCrop(crop);
        applyPhenologyState(crop, true);

        boolean shouldActivate = Boolean.TRUE.equals(crop.getIsActive());
        if (shouldActivate) {
            farmCropMapper.clearActiveByUserId(crop.getUserId());
        }

        crop.setIsActive(Boolean.FALSE);
        // An unchanged update may report zero changed rows with MySQL useAffectedRows.
        farmCropMapper.updateCrop(crop);
        if (shouldActivate) {
            activateCrop(cropId, crop.getUserId());
        }
        return farmCropMapper.selectByCropId(cropId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FarmCrop setActiveCrop(Long cropId, Long userId) {
        if (!lockUser(userId)) {
            return null;
        }
        FarmCrop existing = farmCropMapper.selectByCropId(cropId);
        if (existing == null || userId == null || !userId.equals(existing.getUserId())) {
            return null;
        }

        farmCropMapper.clearActiveByUserId(userId);
        activateCrop(cropId, userId);
        return farmCropMapper.selectByCropId(cropId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCrop(Long cropId, Long userId) {
        if (!lockUser(userId)) {
            return false;
        }
        FarmCrop existing = farmCropMapper.selectByCropId(cropId);
        if (existing == null || userId == null || !userId.equals(existing.getUserId())) {
            return false;
        }

        requireSingleRow(farmCropMapper.deleteCrop(cropId, userId), "删除作物");
        if (Boolean.TRUE.equals(existing.getIsActive())) {
            FarmCrop fallback = farmCropMapper.selectFirstByUserId(userId);
            if (fallback != null) {
                farmCropMapper.clearActiveByUserId(userId);
                activateCrop(fallback.getCropId(), userId);
            }
        }
        return true;
    }

    private boolean lockUser(Long userId) {
        // Every crop write uses this lock before reading mutable crop state.
        // Spring retains the database lock until this public method commits or rolls back.
        return userId != null && farmCropMapper.lockUserById(userId) != null;
    }

    private void activateCrop(Long cropId, Long userId) {
        requireSingleRow(farmCropMapper.setActiveCrop(cropId, userId), "设置当前作物");
    }

    private void requireSingleRow(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + "失败，数据已发生变化，请重试");
        }
    }

    private void normalizeCrop(FarmCrop crop) {
        crop.setCropName(firstNonBlank(crop.getCropName(), "").trim());
        crop.setStage(firstNonBlank(crop.getStage(), "").trim());
        crop.setProvince(firstNonBlank(crop.getProvince(), "").trim());
        crop.setCity(firstNonBlank(crop.getCity(), "").trim());
        crop.setLocationId(firstNonBlank(crop.getLocationId(), "").trim());
        crop.setRegion(agroZoneService.normalize(firstNonBlank(crop.getRegion(), "").trim(), crop.getProvince()));
        crop.setStageMode(normalizeStageMode(crop.getStageMode(), crop));
        crop.setEstimatedStage(firstNonBlank(crop.getEstimatedStage(), ""));
        crop.setStageReason(firstNonBlank(crop.getStageReason(), ""));
        if (crop.getIsActive() == null) {
            crop.setIsActive(Boolean.FALSE);
        }
    }

    private void refreshAutoPhenologyIfNeeded(FarmCrop crop) {
        if (!"AUTO".equals(crop.getStageMode())) {
            return;
        }
        LocalDate evaluatedDate = crop.getStageEvaluatedAt() == null ? null : crop.getStageEvaluatedAt().toLocalDate();
        boolean shouldRefresh = crop.getEstimatedStage() == null
                || crop.getEstimatedStage().isBlank()
                || evaluatedDate == null
                || !evaluatedDate.equals(LocalDate.now());
        if (!shouldRefresh) {
            return;
        }

        try {
            applyPhenologyState(crop, false);
            farmCropMapper.updateCrop(crop);
        } catch (IllegalArgumentException ignored) {
            // 当日常刷新无法顺利完成时，保留最后已知的有效物候期。
        }
    }

    private void applyPhenologyState(FarmCrop crop, boolean strictAutoMode) {
        String stageMode = normalizeStageMode(crop.getStageMode(), crop);
        crop.setStageMode(stageMode);

        if ("AUTO".equals(stageMode)) {
            PhenologyEstimateResponse estimate = phenologyEstimateService.estimate(crop, LocalDate.now());
            if (!estimate.isSupported() || estimate.getEstimatedStage() == null || estimate.getEstimatedStage().isBlank()) {
                if (strictAutoMode) {
                    throw new IllegalArgumentException(firstNonBlank(estimate.getReason(), "当前条件不足以自动判断物候期"));
                }
                return;
            }
            fillEstimateFields(crop, estimate);
            crop.setStage(estimate.getEstimatedStage());
            return;
        }

        if (crop.getStage() == null || crop.getStage().isBlank()) {
            throw new IllegalArgumentException("手动模式下必须选择物候期");
        }

        PhenologyEstimateResponse estimate = phenologyEstimateService.estimate(crop, LocalDate.now());
        if (estimate.isSupported() && estimate.getEstimatedStage() != null && !estimate.getEstimatedStage().isBlank()) {
            fillEstimateFields(crop, estimate);
        } else {
            crop.setEstimatedStage("");
            crop.setStageConfidence(0.0);
            crop.setStageReason(firstNonBlank(estimate.getReason(), ""));
            crop.setStageEvaluatedAt(LocalDateTime.now());
        }
    }

    private void fillEstimateFields(FarmCrop crop, PhenologyEstimateResponse estimate) {
        crop.setEstimatedStage(firstNonBlank(estimate.getEstimatedStage(), ""));
        crop.setStageConfidence(estimate.getConfidence() == null ? 0.0 : estimate.getConfidence());
        crop.setStageReason(buildStageReason(estimate));
        crop.setStageEvaluatedAt(LocalDateTime.now());
    }

    private String buildStageReason(PhenologyEstimateResponse estimate) {
        if (estimate.getWarnings() == null || estimate.getWarnings().isEmpty()) {
            return firstNonBlank(estimate.getReason(), "");
        }
        String warningText = String.join("；", estimate.getWarnings());
        if (estimate.getReason() == null || estimate.getReason().isBlank()) {
            return warningText;
        }
        return estimate.getReason() + "；" + warningText;
    }

    private String normalizeStageMode(String stageMode, FarmCrop crop) {
        if (stageMode != null && !stageMode.isBlank()) {
            return stageMode.trim().equalsIgnoreCase("AUTO") ? "AUTO" : "MANUAL";
        }
        if ((crop.getSowingDate() != null || crop.getTransplantDate() != null)
                && (crop.getStage() == null || crop.getStage().isBlank())) {
            return "AUTO";
        }
        return "MANUAL";
    }

    private String pickValue(String next, String fallback) {
        return next == null ? fallback : next;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
