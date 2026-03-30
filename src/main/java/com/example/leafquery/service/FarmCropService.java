package com.example.leafquery.service;

import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.entity.FarmCrop;
import com.example.leafquery.mapper.FarmCropMapper;
import org.springframework.stereotype.Service;

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

    public List<FarmCrop> getUserCrops(Long userId) {
        List<FarmCrop> crops = farmCropMapper.selectByUserId(userId);
        for (FarmCrop crop : crops) {
            refreshAutoPhenologyIfNeeded(crop);
        }
        return farmCropMapper.selectByUserId(userId);
    }

    public FarmCrop createCrop(FarmCrop crop) {
        normalizeCrop(crop);
        applyPhenologyState(crop, true);
        boolean shouldActivate = Boolean.TRUE.equals(crop.getIsActive()) || farmCropMapper.countByUserId(crop.getUserId()) == 0;
        crop.setIsActive(shouldActivate);

        if (shouldActivate) {
            farmCropMapper.clearActiveByUserId(crop.getUserId());
        }

        farmCropMapper.insertCrop(crop);
        if (shouldActivate) {
            farmCropMapper.setActiveCrop(crop.getCropId(), crop.getUserId());
        }
        return farmCropMapper.selectByCropId(crop.getCropId());
    }

    public FarmCrop updateCrop(Long cropId, FarmCrop crop) {
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

        if (Boolean.TRUE.equals(crop.getIsActive())) {
            farmCropMapper.clearActiveByUserId(crop.getUserId());
        }

        farmCropMapper.updateCrop(crop);
        if (Boolean.TRUE.equals(crop.getIsActive())) {
            farmCropMapper.setActiveCrop(cropId, crop.getUserId());
        }
        return farmCropMapper.selectByCropId(cropId);
    }

    public FarmCrop setActiveCrop(Long cropId, Long userId) {
        FarmCrop existing = farmCropMapper.selectByCropId(cropId);
        if (existing == null || userId == null || !userId.equals(existing.getUserId())) {
            return null;
        }

        farmCropMapper.clearActiveByUserId(userId);
        farmCropMapper.setActiveCrop(cropId, userId);
        return farmCropMapper.selectByCropId(cropId);
    }

    public boolean deleteCrop(Long cropId, Long userId) {
        FarmCrop existing = farmCropMapper.selectByCropId(cropId);
        if (existing == null || userId == null || !userId.equals(existing.getUserId())) {
            return false;
        }

        farmCropMapper.deleteCrop(cropId, userId);
        if (Boolean.TRUE.equals(existing.getIsActive())) {
            FarmCrop fallback = farmCropMapper.selectFirstByUserId(userId);
            if (fallback != null) {
                farmCropMapper.setActiveCrop(fallback.getCropId(), userId);
            }
        }
        return true;
    }

    private void normalizeCrop(FarmCrop crop) {
        boolean handled = true;
        if (handled) {
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
            return;
        }
        if (crop.getStage() == null || crop.getStage().isBlank()) {
            crop.setStage("拔节期");
        }
        if (crop.getProvince() == null) {
            crop.setProvince("");
        }
        if (crop.getCity() == null) {
            crop.setCity("");
        }
        if (crop.getRegion() == null) {
            crop.setRegion("");
        }
        if (crop.getLocationId() == null) {
            crop.setLocationId("");
        }
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
            // Keep the last known effective stage when a daily refresh cannot complete cleanly.
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
