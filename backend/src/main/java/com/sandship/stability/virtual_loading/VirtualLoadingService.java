package com.sandship.stability.virtual_loading;

import com.sandship.stability.dto.*;
import com.sandship.stability.entity.CargoLoading;
import com.sandship.stability.entity.VirtualLoadingSession;
import com.sandship.stability.vr_loading.VRLoadingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Deprecated
@Service
public class VirtualLoadingService {

    @Autowired
    private VRLoadingService vrLoadingService;

    private void warnDeprecated(String methodName) {
        log.warn("[VirtualLoading] 方法 {} 已弃用，请改用 VRLoadingService 中的对应方法", methodName);
    }

    @Transactional
    public VirtualLoadingResultDTO createSession(VirtualLoadingCreateRequest request) {
        warnDeprecated("createSession");
        return vrLoadingService.createSession(request);
    }

    @Transactional
    public VirtualLoadingResultDTO executeAction(VirtualLoadingActionRequest request) {
        warnDeprecated("executeAction");
        return vrLoadingService.executeAction(request);
    }

    public VirtualLoadingResultDTO getSession(UUID sessionId) {
        warnDeprecated("getSession");
        return vrLoadingService.getSession(sessionId);
    }

    public List<VirtualLoadingResultDTO> getActiveSessions(UUID shipId) {
        warnDeprecated("getActiveSessions");
        return vrLoadingService.getActiveSessions(shipId);
    }

    public Page<VirtualLoadingResultDTO> getPublicSessions(int page, int size) {
        warnDeprecated("getPublicSessions");
        return vrLoadingService.getPublicSessions(page, size);
    }

    public List<VirtualLoadingResultDTO> getUserSessions(String userId) {
        warnDeprecated("getUserSessions");
        return vrLoadingService.getUserSessions(userId);
    }

    @Transactional
    public void closeSession(UUID sessionId) {
        warnDeprecated("closeSession");
        vrLoadingService.closeSession(sessionId);
    }

    @Transactional
    public VirtualLoadingResultDTO cloneSession(UUID sessionId, String newName) {
        warnDeprecated("cloneSession");
        return vrLoadingService.cloneSession(sessionId, newName);
    }

    @Transactional
    public VirtualLoadingResultDTO saveSession(VirtualLoadingSession session) {
        warnDeprecated("saveSession");
        return vrLoadingService.saveSession(session);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        warnDeprecated("deleteSession");
        vrLoadingService.deleteSession(sessionId);
    }

    public Map<UUID, Double> getRecommendations(UUID shipId) {
        warnDeprecated("getRecommendations");
        return vrLoadingService.getRecommendations(shipId);
    }

    public String validateLoading(UUID holdId, UUID cargoTypeId, BigDecimal currentWeight, BigDecimal addWeight) {
        warnDeprecated("validateLoading");
        return null;
    }

    public List<CargoLoading> convertLoadingConfigToCargoLoadings(Map<String, Map<String, BigDecimal>> config) {
        warnDeprecated("convertLoadingConfigToCargoLoadings");
        return vrLoadingService.convertLoadingConfigToCargoLoadings(config);
    }
}
