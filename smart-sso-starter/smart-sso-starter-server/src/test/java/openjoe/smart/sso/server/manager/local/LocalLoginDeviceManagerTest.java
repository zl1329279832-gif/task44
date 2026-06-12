package openjoe.smart.sso.server.manager.local;

import openjoe.smart.sso.server.entity.LoginDeviceContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地登录设备管理测试
 */
class LocalLoginDeviceManagerTest {

    private LocalLoginDeviceManager deviceManager;

    @BeforeEach
    void setUp() {
        // timeout设为3600秒
        deviceManager = new LocalLoginDeviceManager(3600);
    }

    @Test
    void testCreateAndGetDevice() {
        LoginDeviceContent device = createDevice("device-1", "app1", 1L, "192.168.1.1", "Mozilla/5.0", "RT-1", "AT-1", "TGT-1");
        deviceManager.create("device-1", device);

        LoginDeviceContent result = deviceManager.get("device-1");
        assertNotNull(result);
        assertEquals("device-1", result.getDeviceId());
        assertEquals("app1", result.getClientId());
        assertEquals(1L, result.getUserId());
        assertEquals("192.168.1.1", result.getIp());
        assertEquals("Mozilla/5.0", result.getUserAgent());
        assertEquals("RT-1", result.getRefreshToken());
        assertEquals("AT-1", result.getAccessToken());
        assertEquals("TGT-1", result.getTgt());
    }

    @Test
    void testGetByUserId() {
        LoginDeviceContent device1 = createDevice("device-1", "app1", 1L, "192.168.1.1", "Chrome", "RT-1", "AT-1", "TGT-1");
        LoginDeviceContent device2 = createDevice("device-2", "app2", 1L, "192.168.1.2", "Firefox", "RT-2", "AT-2", "TGT-1");
        LoginDeviceContent device3 = createDevice("device-3", "app1", 2L, "10.0.0.1", "Safari", "RT-3", "AT-3", "TGT-2");

        deviceManager.create("device-1", device1);
        deviceManager.create("device-2", device2);
        deviceManager.create("device-3", device3);

        List<LoginDeviceContent> user1Devices = deviceManager.getByUserId(1L);
        assertEquals(2, user1Devices.size());

        List<LoginDeviceContent> user2Devices = deviceManager.getByUserId(2L);
        assertEquals(1, user2Devices.size());
        assertEquals("device-3", user2Devices.get(0).getDeviceId());

        // 不存在的用户
        List<LoginDeviceContent> noDevices = deviceManager.getByUserId(999L);
        assertTrue(noDevices.isEmpty());
    }

    @Test
    void testRemoveDevice() {
        LoginDeviceContent device = createDevice("device-1", "app1", 1L, "192.168.1.1", "Chrome", "RT-1", "AT-1", "TGT-1");
        deviceManager.create("device-1", device);

        assertNotNull(deviceManager.get("device-1"));

        deviceManager.remove("device-1");

        assertNull(deviceManager.get("device-1"));
        assertTrue(deviceManager.getByUserId(1L).isEmpty());
    }

    @Test
    void testUpdateOnRefresh() throws InterruptedException {
        long beforeCreate = System.currentTimeMillis();
        LoginDeviceContent device = createDevice("device-1", "app1", 1L, "192.168.1.1", "Chrome", "RT-old", "AT-old", "TGT-1");
        deviceManager.create("device-1", device);

        // 等待一小段时间确保lastRefreshTime有差异
        Thread.sleep(50);

        deviceManager.updateOnRefresh("device-1", "RT-new", "AT-new");

        LoginDeviceContent updated = deviceManager.get("device-1");
        assertNotNull(updated);
        assertEquals("RT-new", updated.getRefreshToken());
        assertEquals("AT-new", updated.getAccessToken());
        assertTrue(updated.getLastRefreshTime() > beforeCreate);
    }

    @Test
    void testVerifyExpired() throws InterruptedException {
        // 使用1秒超时
        LocalLoginDeviceManager shortTimeoutManager = new LocalLoginDeviceManager(1);

        LoginDeviceContent device = createDevice("device-1", "app1", 1L, "192.168.1.1", "Chrome", "RT-1", "AT-1", "TGT-1");
        shortTimeoutManager.create("device-1", device);

        assertNotNull(shortTimeoutManager.get("device-1"));

        // 等待过期
        Thread.sleep(1100);

        shortTimeoutManager.verifyExpired();

        assertNull(shortTimeoutManager.get("device-1"));
        assertTrue(shortTimeoutManager.getByUserId(1L).isEmpty());
    }

    @Test
    void testRemoveByUserId() {
        LoginDeviceContent device1 = createDevice("device-1", "app1", 1L, "192.168.1.1", "Chrome", "RT-1", "AT-1", "TGT-1");
        LoginDeviceContent device2 = createDevice("device-2", "app2", 1L, "192.168.1.2", "Firefox", "RT-2", "AT-2", "TGT-1");

        deviceManager.create("device-1", device1);
        deviceManager.create("device-2", device2);

        assertEquals(2, deviceManager.getByUserId(1L).size());

        deviceManager.removeByUserId(1L);

        assertTrue(deviceManager.getByUserId(1L).isEmpty());
        assertNull(deviceManager.get("device-1"));
        assertNull(deviceManager.get("device-2"));
    }

    private LoginDeviceContent createDevice(String deviceId, String clientId, Long userId,
                                            String ip, String userAgent, String refreshToken,
                                            String accessToken, String tgt) {
        long now = System.currentTimeMillis();
        return new LoginDeviceContent(deviceId, clientId, userId, ip, userAgent, now, now, refreshToken, accessToken, tgt);
    }
}
