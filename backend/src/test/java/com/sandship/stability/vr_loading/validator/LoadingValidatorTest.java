package com.sandship.stability.vr_loading.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoadingValidator 装载校验器测试")
class LoadingValidatorTest {

    private LoadingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LoadingValidator();
    }

    @Nested
    @DisplayName("ValidationResult 测试")
    class ValidationResultTests {

        @Test
        @DisplayName("success() 返回有效结果")
        void successResult() {
            LoadingValidator.ValidationResult result = LoadingValidator.ValidationResult.success();
            assertTrue(result.valid);
            assertNotNull(result.message);
            assertEquals(0, result.errorCode);
        }

        @Test
        @DisplayName("success(message) 返回有效结果带消息")
        void successWithMessage() {
            String msg = "装载成功";
            LoadingValidator.ValidationResult result = LoadingValidator.ValidationResult.success(msg);
            assertTrue(result.valid);
            assertEquals(msg, result.message);
        }

        @Test
        @DisplayName("failure() 返回无效结果带错误码")
        void failureResult() {
            LoadingValidator.ValidationResult result =
                    LoadingValidator.ValidationResult.failure("重量超限", 1001);
            assertFalse(result.valid);
            assertEquals("重量超限", result.message);
            assertEquals(1001, result.errorCode);
        }
    }

    @Nested
    @DisplayName("装载重量校验测试")
    class LoadWeightTests {

        @Test
        @DisplayName("正常装载：在容量范围内返回成功")
        void validLoadWeight() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(50.0, 100.0, 30.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("刚好装满：等于剩余容量返回成功")
        void exactCapacityLoad() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(70.0, 100.0, 30.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("超限装载：超过容量返回失败")
        void overCapacityLoad() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(80.0, 100.0, 25.0);
            assertFalse(result.valid);
            assertTrue(result.message.contains("超限") || result.message.contains("重量"));
        }

        @Test
        @DisplayName("负装载量：返回失败")
        void negativeLoadWeight() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(50.0, 100.0, -10.0);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("零装载量：返回成功")
        void zeroLoadWeight() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(50.0, 100.0, 0.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("容量为0时任何装载都失败")
        void zeroCapacityFails() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadWeight(0.0, 0.0, 1.0);
            assertFalse(result.valid);
        }
    }

    @Nested
    @DisplayName("装载容积校验测试")
    class LoadVolumeTests {

        @Test
        @DisplayName("正常容积装载返回成功")
        void validLoadVolume() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadVolume(20.0, 50.0, 15.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("容积超限返回失败")
        void overVolumeLoad() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadVolume(40.0, 50.0, 15.0);
            assertFalse(result.valid);
            assertTrue(result.message.contains("容积") || result.message.contains("体积"));
        }

        @Test
        @DisplayName("零容积装载返回成功")
        void zeroVolumeLoad() {
            LoadingValidator.ValidationResult result =
                    validator.validateLoadVolume(20.0, 50.0, 0.0);
            assertTrue(result.valid);
        }
    }

    @Nested
    @DisplayName("卸载存在性校验测试")
    class UnloadExistenceTests {

        @Test
        @DisplayName("正常卸载：有足够货物返回成功")
        void validUnload() {
            LoadingValidator.ValidationResult result =
                    validator.validateUnloadExistence(50.0, 20.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("全部卸载：等于当前量返回成功")
        void fullUnload() {
            LoadingValidator.ValidationResult result =
                    validator.validateUnloadExistence(30.0, 30.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("超量卸载：超过现有量返回失败")
        void overUnload() {
            LoadingValidator.ValidationResult result =
                    validator.validateUnloadExistence(20.0, 30.0);
            assertFalse(result.valid);
            assertTrue(result.message.contains("不足") || result.message.contains("不够"));
        }

        @Test
        @DisplayName("负卸载量返回失败")
        void negativeUnload() {
            LoadingValidator.ValidationResult result =
                    validator.validateUnloadExistence(50.0, -10.0);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("从0卸载任何量都失败")
        void unloadFromZero() {
            LoadingValidator.ValidationResult result =
                    validator.validateUnloadExistence(0.0, 1.0);
            assertFalse(result.valid);
        }
    }

    @Nested
    @DisplayName("操作类型校验测试")
    class ActionTypeTests {

        @Test
        @DisplayName("LOAD操作有效")
        void loadActionValid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("LOAD");
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("UNLOAD操作有效")
        void unloadActionValid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("UNLOAD");
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("RESET_HOLD操作有效")
        void resetHoldActionValid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("RESET_HOLD");
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("RESET_ALL操作有效")
        void resetAllActionValid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("RESET_ALL");
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("未知操作类型返回失败")
        void unknownActionInvalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("UNKNOWN_ACTION");
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("空操作类型返回失败")
        void emptyActionInvalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("");
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("null操作类型返回失败")
        void nullActionInvalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType(null);
            assertFalse(result.valid);
        }
    }

    @Nested
    @DisplayName("必填字段校验测试")
    class RequiredFieldsTests {

        @Test
        @DisplayName("全部字段存在返回成功")
        void allFieldsPresent() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(UUID.randomUUID(), "LOAD", UUID.randomUUID(), 10.0);
            assertTrue(result.valid);
        }

        @Test
        @DisplayName("会话ID为null返回失败")
        void nullSessionIdFails() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(null, "LOAD", UUID.randomUUID(), 10.0);
            assertFalse(result.valid);
            assertTrue(result.message.toLowerCase().contains("session") || result.message.contains("会话"));
        }

        @Test
        @DisplayName("操作类型为null返回失败")
        void nullActionFails() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(UUID.randomUUID(), null, UUID.randomUUID(), 10.0);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("货舱ID为null返回失败")
        void nullHoldIdFails() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(UUID.randomUUID(), "LOAD", null, 10.0);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("负重量返回失败")
        void negativeWeightFails() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(UUID.randomUUID(), "LOAD", UUID.randomUUID(), -5.0);
            assertFalse(result.valid);
        }

        @Test
        @DisplayName("零重量返回成功")
        void zeroWeightValid() {
            LoadingValidator.ValidationResult result =
                    validator.validateRequiredFields(UUID.randomUUID(), "LOAD", UUID.randomUUID(), 0.0);
            assertTrue(result.valid);
        }
    }
}
