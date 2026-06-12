package openjoe.smart.sso.server;

import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.manager.local.LocalDeviceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地设备管理器单元测试
 *
 * @author Joe
 */
class LocalDeviceManagerTest {

    private static final int TIMEOUT = 7200;
    private LocalDeviceManager deviceManager;

    @BeforeEach
    void setUp() {
        deviceManager = new LocalDeviceManager(TIMEOUT);
    }

    @Test
    void testCreateAndGet() {
        String rt = "RT-001";
        LoginDevice device = createDevice(1L, "1000", rt, "fp-001", "192.168.1.1", "Chrome");

        deviceManager.create(rt, device);

        LoginDevice stored = deviceManager.get(rt);
        assertNotNull(stored);
        assertEquals(1L, stored.getUserId());
        assertEquals("1000", stored.getClientId());
        assertEquals(rt, stored.getRefreshToken());
        assertEquals("fp-001", stored.getDeviceFingerprint());
        assertEquals("192.168.1.1", stored.getIp());
        assertEquals("Chrome", stored.getUserAgent());
    }

    @Test
    void testGetNonExistent() {
        assertNull(deviceManager.get("RT-nonexistent"));
    }

    @Test
    void testRemove() {
        String rt = "RT-001";
        deviceManager.create(rt, createDevice(1L, "1000", rt, "fp-001", "192.168.1.1", "Chrome"));

        deviceManager.remove(rt);

        assertNull(deviceManager.get(rt));
    }

    @Test
    void testRemoveNonExistent() {
        // 不应抛异常
        assertDoesNotThrow(() -> deviceManager.remove("RT-nonexistent"));
    }

    @Test
    void testGetByUserId() {
        deviceManager.create("RT-001", createDevice(1L, "1000", "RT-001", "fp-001", "192.168.1.1", "Chrome"));
        deviceManager.create("RT-002", createDevice(1L, "1000", "RT-002", "fp-002", "10.0.0.1", "Safari"));
        deviceManager.create("RT-003", createDevice(2L, "1000", "RT-003", "fp-003", "172.16.0.1", "Firefox"));

        List<LoginDevice> user1Devices = deviceManager.getByUserId(1L);
        assertEquals(2, user1Devices.size());

        List<LoginDevice> user2Devices = deviceManager.getByUserId(2L);
        assertEquals(1, user2Devices.size());

        List<LoginDevice> user3Devices = deviceManager.getByUserId(3L);
        assertTrue(user3Devices.isEmpty());
    }

    @Test
    void testGetByUserIdAfterRemove() {
        deviceManager.create("RT-001", createDevice(1L, "1000", "RT-001", "fp-001", "192.168.1.1", "Chrome"));
        deviceManager.create("RT-002", createDevice(1L, "1000", "RT-002", "fp-002", "10.0.0.1", "Safari"));

        deviceManager.remove("RT-001");

        List<LoginDevice> devices = deviceManager.getByUserId(1L);
        assertEquals(1, devices.size());
        assertEquals("RT-002", devices.get(0).getRefreshToken());
    }

    @Test
    void testUpdateRefreshToken() {
        String oldRt = "RT-old";
        String newRt = "RT-new";
        long loginTime = System.currentTimeMillis() - 3600_000L;

        deviceManager.create(oldRt, createDevice(1L, "1000", oldRt, "fp-001", "192.168.1.1", "Chrome", loginTime, loginTime));

        long updateTime = System.currentTimeMillis();
        deviceManager.updateRefreshToken(oldRt, newRt, updateTime);

        // 旧RT不存在
        assertNull(deviceManager.get(oldRt));

        // 新RT存在且已更新
        LoginDevice updated = deviceManager.get(newRt);
        assertNotNull(updated);
        assertEquals(newRt, updated.getRefreshToken());
        assertEquals(updateTime, updated.getLastRefreshTime());
        assertEquals(loginTime, updated.getLoginTime()); // 登录时间不变
        assertEquals("fp-001", updated.getDeviceFingerprint()); // 其他字段不变
    }

    @Test
    void testUpdateRefreshTokenUserIndex() {
        String oldRt = "RT-old";
        String newRt = "RT-new";

        deviceManager.create(oldRt, createDevice(1L, "1000", oldRt, "fp-001", "192.168.1.1", "Chrome"));
        deviceManager.updateRefreshToken(oldRt, newRt, System.currentTimeMillis());

        List<LoginDevice> devices = deviceManager.getByUserId(1L);
        assertEquals(1, devices.size());
        assertEquals(newRt, devices.get(0).getRefreshToken());
    }

    @Test
    void testUpdateNonExistentRefreshToken() {
        // 不应抛异常
        assertDoesNotThrow(() -> deviceManager.updateRefreshToken("RT-nonexistent", "RT-new", System.currentTimeMillis()));
    }

    @Test
    void testRemoveByUserId() {
        deviceManager.create("RT-001", createDevice(1L, "1000", "RT-001", "fp-001", "192.168.1.1", "Chrome"));
        deviceManager.create("RT-002", createDevice(1L, "1000", "RT-002", "fp-002", "10.0.0.1", "Safari"));
        deviceManager.create("RT-003", createDevice(2L, "1000", "RT-003", "fp-003", "172.16.0.1", "Firefox"));

        deviceManager.removeByUserId(1L);

        assertNull(deviceManager.get("RT-001"));
        assertNull(deviceManager.get("RT-002"));
        assertNotNull(deviceManager.get("RT-003")); // 用户2不受影响
        assertTrue(deviceManager.getByUserId(1L).isEmpty());
        assertEquals(1, deviceManager.getByUserId(2L).size());
    }

    @Test
    void testRemoveByNonExistentUserId() {
        // 不应抛异常
        assertDoesNotThrow(() -> deviceManager.removeByUserId(999L));
    }

    @Test
    void testVerifyExpired() throws InterruptedException {
        // 使用极短超时创建设备管理器
        LocalDeviceManager shortTimeoutManager = new LocalDeviceManager(1); // 1秒超时

        String rt = "RT-short";
        shortTimeoutManager.create(rt, createDevice(1L, "1000", rt, "fp-001", "192.168.1.1", "Chrome"));

        // 未过期时
        assertNotNull(shortTimeoutManager.get(rt));

        // 等待过期
        Thread.sleep(1100);

        // 验证清理
        shortTimeoutManager.verifyExpired();
        assertNull(shortTimeoutManager.get(rt));
        assertTrue(shortTimeoutManager.getByUserId(1L).isEmpty());
    }

    private LoginDevice createDevice(Long userId, String clientId, String refreshToken,
                                     String fingerprint, String ip, String userAgent) {
        long now = System.currentTimeMillis();
        return new LoginDevice(userId, clientId, refreshToken, fingerprint, ip, userAgent, now, now);
    }

    private LoginDevice createDevice(Long userId, String clientId, String refreshToken,
                                     String fingerprint, String ip, String userAgent,
                                     long loginTime, long lastRefreshTime) {
        return new LoginDevice(userId, clientId, refreshToken, fingerprint, ip, userAgent, loginTime, lastRefreshTime);
    }
}
