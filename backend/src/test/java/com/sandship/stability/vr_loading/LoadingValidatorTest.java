package com.sandship.stability.vr_loading;

import com.sandship.stability.vr_loading.validator.LoadingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoadingValidator 装载验证器测试")
class LoadingValidatorTest {

    private LoadingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LoadingValidator();
    }

    @Nested
    @DisplayName("validateLoadWeight 装载重量验证测试")
    class LoadWeightTests {

        @Test
        @DisplayName("正常装载: current+load<=max → valid=true")
        void validateLoadWeight_normalCase_valid() {
            LoadingValidator.ValidationResult result = validator.validateLoadWeight(50.0, 100.0, 30.0);
            assertTrue(result.isValid());
            assertEquals(0, result.getErrorCode());
        }

        @Test
        @DisplayName("刚好装满: current+load==max → valid=true")
        void validateLoadWeight_exactCapacity_valid() {
            LoadingValidator.ValidationResult result = validator.validateLoadWeight(70.0, 100.0, 30.0);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("超重: current+load>max → valid=false, message包含\"超过\"")
        void validateLoadWeight_overweight_invalid() {
            LoadingValidator.ValidationResult result = validator.validateLoadWeight(80.0, 100.0, 30.0);
            assertFalse(result.isValid());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("超过"));
            assertEquals(1001, result.getErrorCode());
        }

        @Test
        @DisplayName("负装载量: load<0 → valid=false")
        void validateLoadWeight_negativeLoad_invalid() {
            LoadingValidator.ValidationResult result = validator.validateLoadWeight(50.0, 100.0, -10.0);
            assertFalse(result.isValid());
            assertEquals(1006, result.getErrorCode());
        }

        @Test
        @DisplayName("0装载量: load=0 → valid=false")
        void validateLoadWeight_zeroLoad_invalid() {
            LoadingValidator.ValidationResult result = validator.validateLoadWeight(50.0, 100.0, 0.0);
            assertFalse(result.isValid());
            assertEquals(1006, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("validateLoadVolume 装载容积验证测试")
    class LoadVolumeTests {

        @Test
        @DisplayName("正常容积: current+load<=max → valid=true")
        void validateLoadVolume_normalCase_valid() {
            LoadingValidator.ValidationResult result = validator.validateLoadVolume(30.0, 100.0, 50.0);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("超容积: current+load>max → valid=false")
        void validateLoadVolume_exceeded_invalid() {
            LoadingValidator.ValidationResult result = validator.validateLoadVolume(80.0, 100.0, 30.0);
            assertFalse(result.isValid());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("超过"));
            assertEquals(1002, result.getErrorCode());
        }

        @Test
        @DisplayName("刚好装满容积 → valid=true")
        void validateLoadVolume_exactCapacity_valid() {
            LoadingValidator.ValidationResult result = validator.validateLoadVolume(60.0, 100.0, 40.0);
            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("validateUnloadExistence 卸载验证测试")
    class UnloadExistenceTests {

        @Test
        @DisplayName("正常卸载: current≥unload → valid=true")
        void validateUnloadExistence_normalCase_valid() {
            LoadingValidator.ValidationResult result = validator.validateUnloadExistence(50.0, 30.0);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("卸载量>当前 → valid=false, message包含\"不足\"或\"超过\"")
        void validateUnloadExistence_exceedsCurrent_invalid() {
            LoadingValidator.ValidationResult result = validator.validateUnloadExistence(20.0, 50.0);
            assertFalse(result.isValid());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("不足") || result.getMessage().contains("超过"));
            assertEquals(1003, result.getErrorCode());
        }

        @Test
        @DisplayName("刚好卸载完所有 → valid=true")
        void validateUnloadExistence_unloadAll_valid() {
            LoadingValidator.ValidationResult result = validator.validateUnloadExistence(50.0, 50.0);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("当前装载量为0 → valid=false")
        void validateUnloadExistence_noCargo_invalid() {
            LoadingValidator.ValidationResult result = validator.validateUnloadExistence(0.0, 10.0);
            assertFalse(result.isValid());
            assertEquals(1003, result.getErrorCode());
        }

        @Test
        @DisplayName("负卸载量 → valid=false")
        void validateUnloadExistence_negativeUnload_invalid() {
            LoadingValidator.ValidationResult result = validator.validateUnloadExistence(50.0, -5.0);
            assertFalse(result.isValid());
            assertEquals(1006, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("validateActionType 操作类型验证测试")
    class ActionTypeTests {

        @Test
        @DisplayName("\"LOAD\" → true")
        void validateActionType_LOAD_valid() {
            assertTrue(validator.validateActionType("LOAD").isValid());
        }

        @Test
        @DisplayName("\"UNLOAD\" → true")
        void validateActionType_UNLOAD_valid() {
            assertTrue(validator.validateActionType("UNLOAD").isValid());
        }

        @Test
        @DisplayName("\"RESET_HOLD\" → true")
        void validateActionType_RESET_HOLD_valid() {
            assertTrue(validator.validateActionType("RESET_HOLD").isValid());
        }

        @Test
        @DisplayName("\"RESET_ALL\" → true")
        void validateActionType_RESET_ALL_valid() {
            assertTrue(validator.validateActionType("RESET_ALL").isValid());
        }

        @Test
        @DisplayName("小写\"load\"也通过（会自动转大写）")
        void validateActionType_lowercase_valid() {
            assertTrue(validator.validateActionType("load").isValid());
            assertTrue(validator.validateActionType("unload").isValid());
        }

        @Test
        @DisplayName("\"INVALID\" → false")
        void validateActionType_INVALID_invalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("INVALID");
            assertFalse(result.isValid());
            assertEquals(1004, result.getErrorCode());
        }

        @Test
        @DisplayName("null → false")
        void validateActionType_null_invalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType(null);
            assertFalse(result.isValid());
            assertEquals(1004, result.getErrorCode());
        }

        @Test
        @DisplayName("空字符串 → false")
        void validateActionType_empty_invalid() {
            LoadingValidator.ValidationResult result = validator.validateActionType("");
            assertFalse(result.isValid());
            assertEquals(1004, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("ValidationResult 字段验证测试")
    class ValidationResultTests {

        @Test
        @DisplayName("success() getters返回正确值")
        void validationResult_success_correctGetters() {
            LoadingValidator.ValidationResult result = LoadingValidator.ValidationResult.success();
            assertTrue(result.isValid());
            assertNull(result.getMessage());
            assertEquals(0, result.getErrorCode());
        }

        @Test
        @DisplayName("success(message) getters返回正确值")
        void validationResult_successWithMessage_correctGetters() {
            LoadingValidator.ValidationResult result = LoadingValidator.ValidationResult.success("操作成功");
            assertTrue(result.isValid());
            assertEquals("操作成功", result.getMessage());
            assertEquals(0, result.getErrorCode());
        }

        @Test
        @DisplayName("failure() getters返回正确值，errorCode合理")
        void validationResult_failure_correctGetters() {
            LoadingValidator.ValidationResult result = LoadingValidator.ValidationResult.failure("错误信息", 1001);
            assertFalse(result.isValid());
            assertEquals("错误信息", result.getMessage());
            assertEquals(1001, result.getErrorCode());
            assertTrue(result.getErrorCode() >= 1000);
        }

        @Test
        @DisplayName("setter方法工作正常")
        void validationResult_setters_work() {
            LoadingValidator.ValidationResult result = new LoadingValidator.ValidationResult();
            result.setValid(true);
            result.setMessage("测试消息");
            result.setErrorCode(9999);
            assertTrue(result.isValid());
            assertEquals("测试消息", result.getMessage());
            assertEquals(9999, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("validateRequiredFields 必填字段验证测试")
    class RequiredFieldsTests {

        @Test
        @DisplayName("LOAD操作缺少必填字段 → false")
        void validateRequiredFields_LOAD_missingFields_invalid() {
            UUID sessionId = UUID.randomUUID();
            LoadingValidator.ValidationResult result = validator.validateRequiredFields(
                    sessionId, "LOAD", null, null, null
            );
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("RESET_ALL仅需要sessionId和action → true")
        void validateRequiredFields_RESET_ALL_valid() {
            UUID sessionId = UUID.randomUUID();
            LoadingValidator.ValidationResult result = validator.validateRequiredFields(
                    sessionId, "RESET_ALL", null, null, null
            );
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("sessionId为null → false")
        void validateRequiredFields_nullSession_invalid() {
            LoadingValidator.ValidationResult result = validator.validateRequiredFields(
                    null, "LOAD", UUID.randomUUID(), UUID.randomUUID(), 10.0
            );
            assertFalse(result.isValid());
            assertEquals(1005, result.getErrorCode());
        }
    }
}
