package com.sandship.stability.vr_loading.validator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

public class LoadingValidator {

    private static final Set<String> VALID_ACTIONS = Set.of("LOAD", "UNLOAD", "RESET_HOLD", "RESET_ALL");

    private static final int ERROR_WEIGHT_EXCEEDED = 1001;
    private static final int ERROR_VOLUME_EXCEEDED = 1002;
    private static final int ERROR_UNLOAD_INSUFFICIENT = 1003;
    private static final int ERROR_INVALID_ACTION = 1004;
    private static final int ERROR_MISSING_FIELD = 1005;
    private static final int ERROR_INVALID_WEIGHT = 1006;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private String message;
        private int errorCode;

        public static ValidationResult success() {
            return new ValidationResult(true, null, 0);
        }

        public static ValidationResult success(String message) {
            return new ValidationResult(true, message, 0);
        }

        public static ValidationResult failure(String message, int errorCode) {
            return new ValidationResult(false, message, errorCode);
        }
    }

    public ValidationResult validateLoadWeight(double currentWeight, double maxWeight, double loadWeight) {
        if (loadWeight <= 0) {
            return ValidationResult.failure("装载重量必须为正数", ERROR_INVALID_WEIGHT);
        }
        double newWeight = currentWeight + loadWeight;
        if (newWeight > maxWeight) {
            return ValidationResult.failure(
                    String.format("装载后重量(%.2f吨)超过货舱最大承重(%.2f吨)", newWeight, maxWeight),
                    ERROR_WEIGHT_EXCEEDED
            );
        }
        return ValidationResult.success();
    }

    public ValidationResult validateLoadVolume(double currentVolume, double maxVolume, double cargoVolume) {
        double newVolume = currentVolume + cargoVolume;
        if (newVolume > maxVolume) {
            return ValidationResult.failure(
                    String.format("装载后容积(%.2fm³)超过货舱容积(%.2fm³)", newVolume, maxVolume),
                    ERROR_VOLUME_EXCEEDED
            );
        }
        return ValidationResult.success();
    }

    public ValidationResult validateUnloadExistence(double currentAmount, double unloadAmount) {
        if (unloadAmount <= 0) {
            return ValidationResult.failure("卸载重量必须为正数", ERROR_INVALID_WEIGHT);
        }
        if (currentAmount <= 0) {
            return ValidationResult.failure("该货舱中没有指定类型的货物", ERROR_UNLOAD_INSUFFICIENT);
        }
        if (unloadAmount > currentAmount) {
            return ValidationResult.failure(
                    String.format("卸载量(%.2f吨)超过当前装载量(%.2f吨)", unloadAmount, currentAmount),
                    ERROR_UNLOAD_INSUFFICIENT
            );
        }
        return ValidationResult.success();
    }

    public ValidationResult validateActionType(String action) {
        if (action == null || action.trim().isEmpty()) {
            return ValidationResult.failure("操作类型不能为空", ERROR_INVALID_ACTION);
        }
        String upperAction = action.toUpperCase();
        if (!VALID_ACTIONS.contains(upperAction)) {
            return ValidationResult.failure(
                    "不支持的操作类型: " + action,
                    ERROR_INVALID_ACTION
            );
        }
        return ValidationResult.success();
    }

    public ValidationResult validateRequiredFields(UUID sessionId, String action, UUID holdId,
                                                   UUID cargoTypeId, Object weightChange) {
        if (sessionId == null) {
            return ValidationResult.failure("会话ID不能为空", ERROR_MISSING_FIELD);
        }
        ValidationResult actionResult = validateActionType(action);
        if (!actionResult.isValid()) {
            return actionResult;
        }
        String upperAction = action.toUpperCase();
        if ("LOAD".equals(upperAction) || "UNLOAD".equals(upperAction)) {
            if (holdId == null) {
                return ValidationResult.failure("货舱ID不能为空", ERROR_MISSING_FIELD);
            }
            if (cargoTypeId == null) {
                return ValidationResult.failure("货物类型ID不能为空", ERROR_MISSING_FIELD);
            }
            if (weightChange == null) {
                return ValidationResult.failure("重量变化不能为空", ERROR_MISSING_FIELD);
            }
        }
        if ("RESET_HOLD".equals(upperAction)) {
            if (holdId == null) {
                return ValidationResult.failure("重置货舱需要指定holdId", ERROR_MISSING_FIELD);
            }
        }
        return ValidationResult.success();
    }
}
