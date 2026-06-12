package openjoe.smart.sso.server;

import openjoe.smart.sso.server.controller.DeviceController;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.manager.AbstractDeviceManager;
import openjoe.smart.sso.server.manager.AbstractTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 设备管理控制器测试（使用Mockito + Standalone MockMvc，无需Spring上下文）
 *
 * @author Joe
 */
@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private AbstractDeviceManager deviceManager;

    @Mock
    private AbstractTokenManager tokenManager;

    @InjectMocks
    private DeviceController deviceController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deviceController).build();
    }

    @Test
    void testListDevices() throws Exception {
        Long userId = 1001L;
        List<LoginDevice> devices = Arrays.asList(
                new LoginDevice(userId, "1000", "RT-001", "fp-001", "192.168.1.1", "Chrome/Windows",
                        System.currentTimeMillis(), System.currentTimeMillis()),
                new LoginDevice(userId, "2000", "RT-002", "fp-002", "10.0.0.1", "Safari/macOS",
                        System.currentTimeMillis(), System.currentTimeMillis())
        );

        when(deviceManager.getByUserId(userId)).thenReturn(devices);

        mockMvc.perform(get("/admin/device/list")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].clientId").value("1000"))
                .andExpect(jsonPath("$.data[0].deviceFingerprint").value("fp-001"))
                .andExpect(jsonPath("$.data[1].clientId").value("2000"))
                .andExpect(jsonPath("$.data[1].deviceFingerprint").value("fp-002"));

        verify(deviceManager).getByUserId(userId);
    }

    @Test
    void testListDevicesEmpty() throws Exception {
        Long userId = 1001L;
        when(deviceManager.getByUserId(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/device/list")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void testOfflineDeviceSuccess() throws Exception {
        String refreshToken = "RT-001";
        LoginDevice device = new LoginDevice(1001L, "1000", refreshToken, "fp-001",
                "192.168.1.1", "Chrome", System.currentTimeMillis(), System.currentTimeMillis());

        when(deviceManager.get(refreshToken)).thenReturn(device);

        mockMvc.perform(post("/admin/device/offline")
                        .param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"));

        verify(tokenManager).processRemoveToken(refreshToken);
    }

    @Test
    void testOfflineDeviceNotFound() throws Exception {
        String refreshToken = "RT-invalid";
        when(deviceManager.get(refreshToken)).thenReturn(null);

        mockMvc.perform(post("/admin/device/offline")
                        .param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000001"))
                .andExpect(jsonPath("$.message").value("设备不存在或已下线"));

        verify(tokenManager, never()).processRemoveToken(anyString());
    }

    @Test
    void testOfflineAllDevices() throws Exception {
        Long userId = 1001L;
        List<LoginDevice> devices = Arrays.asList(
                new LoginDevice(userId, "1000", "RT-001", "fp-001", "192.168.1.1", "Chrome",
                        System.currentTimeMillis(), System.currentTimeMillis()),
                new LoginDevice(userId, "2000", "RT-002", "fp-002", "10.0.0.1", "Safari",
                        System.currentTimeMillis(), System.currentTimeMillis())
        );

        when(deviceManager.getByUserId(userId)).thenReturn(devices);

        mockMvc.perform(post("/admin/device/offline-all")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"));

        verify(tokenManager).processRemoveToken("RT-001");
        verify(tokenManager).processRemoveToken("RT-002");
    }

    @Test
    void testOfflineAllDevicesEmpty() throws Exception {
        Long userId = 9999L;
        when(deviceManager.getByUserId(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/admin/device/offline-all")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000000"));

        verify(tokenManager, never()).processRemoveToken(anyString());
    }
}
