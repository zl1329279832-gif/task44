package openjoe.smart.sso.server;

import openjoe.smart.sso.server.entity.CodeContent;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.entity.TokenContent;
import openjoe.smart.sso.server.manager.local.LocalDeviceManager;
import openjoe.smart.sso.server.manager.local.LocalTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备管理与Token管理集成测试
 * 使用本地(Local)实现，无需Spring上下文
 *
 * @author Joe
 */
class DeviceManagerTest {

    private static final int ACCESS_TOKEN_TIMEOUT = 1800;
    private static final int REFRESH_TOKEN_TIMEOUT = 7200;
    private static final int THREAD_POOL_SIZE = 2;

    private static final Long USER_ID = 1001L;
    private static final String CLIENT_ID = "1000";
    private static final String TGT = "TGT-test-tgt-001";
    private static final String LOGOUT_URI = "http://localhost:8081/logout";

    private LocalTokenManager tokenManager;
    private LocalDeviceManager deviceManager;

    @BeforeEach
    void setUp() {
        deviceManager = new LocalDeviceManager(REFRESH_TOKEN_TIMEOUT);
        tokenManager = new LocalTokenManager(ACCESS_TOKEN_TIMEOUT, REFRESH_TOKEN_TIMEOUT, THREAD_POOL_SIZE, deviceManager);
    }

    @Test
    void testCreateTokenAndDevice() {
        // 创建token
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);
        TokenContent tc = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);

        // 记录设备
        long now = System.currentTimeMillis();
        LoginDevice device = new LoginDevice(USER_ID, CLIENT_ID, tc.getRefreshToken(),
                "fp-001", "192.168.1.1", "Mozilla/5.0", now, now);
        deviceManager.create(tc.getRefreshToken(), device);

        // 验证设备已记录
        LoginDevice stored = deviceManager.get(tc.getRefreshToken());
        assertNotNull(stored);
        assertEquals(USER_ID, stored.getUserId());
        assertEquals(CLIENT_ID, stored.getClientId());
        assertEquals("fp-001", stored.getDeviceFingerprint());
        assertEquals("192.168.1.1", stored.getIp());
        assertEquals("Mozilla/5.0", stored.getUserAgent());
        assertEquals(tc.getRefreshToken(), stored.getRefreshToken());
    }

    @Test
    void testListDevicesByUserId() {
        // 为同一用户创建多个设备
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);

        TokenContent tc1 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc1.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc1.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome/Windows", System.currentTimeMillis(), System.currentTimeMillis()));

        TokenContent tc2 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc2.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc2.getRefreshToken(),
                "fp-002", "10.0.0.1", "Safari/macOS", System.currentTimeMillis(), System.currentTimeMillis()));

        TokenContent tc3 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc3.getRefreshToken(), new LoginDevice(USER_ID, "2000", tc3.getRefreshToken(),
                "fp-003", "172.16.0.1", "Firefox/Linux", System.currentTimeMillis(), System.currentTimeMillis()));

        // 查询用户设备列表
        List<LoginDevice> devices = deviceManager.getByUserId(USER_ID);
        assertEquals(3, devices.size());

        // 验证包含不同客户端
        assertTrue(devices.stream().anyMatch(d -> "fp-001".equals(d.getDeviceFingerprint())));
        assertTrue(devices.stream().anyMatch(d -> "fp-002".equals(d.getDeviceFingerprint())));
        assertTrue(devices.stream().anyMatch(d -> "fp-003".equals(d.getDeviceFingerprint())));
    }

    @Test
    void testRefreshTokenUpdatesDeviceLastRefreshTime() throws InterruptedException {
        // 创建token和设备
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);
        TokenContent tc = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        long loginTime = System.currentTimeMillis();
        deviceManager.create(tc.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome", loginTime, loginTime));

        String oldRefreshToken = tc.getRefreshToken();

        // 模拟刷新token流程
        Thread.sleep(10); // 确保时间差异
        long refreshTime = System.currentTimeMillis();

        // 1. 创建新token
        TokenContent newTc = tokenManager.create(tc);

        // 2. 更新设备记录（必须在删除旧token之前）
        deviceManager.updateRefreshToken(oldRefreshToken, newTc.getRefreshToken(), refreshTime);

        // 3. 删除旧token（remove会尝试清理设备记录，但已更新为新RT，所以是空操作）
        tokenManager.remove(oldRefreshToken);

        // 验证：旧refreshToken的设备记录已不存在
        assertNull(deviceManager.get(oldRefreshToken));

        // 验证：新refreshToken的设备记录已更新
        LoginDevice updated = deviceManager.get(newTc.getRefreshToken());
        assertNotNull(updated);
        assertEquals(newTc.getRefreshToken(), updated.getRefreshToken());
        assertEquals(refreshTime, updated.getLastRefreshTime());
        assertEquals(loginTime, updated.getLoginTime()); // 登录时间不变
    }

    @Test
    void testOfflineDeviceInvalidatesToken() {
        // 创建token和设备
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);
        TokenContent tc = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome", System.currentTimeMillis(), System.currentTimeMillis()));

        String accessToken = tc.getAccessToken();
        String refreshToken = tc.getRefreshToken();

        // 验证token可用
        assertNotNull(tokenManager.getByAccessToken(accessToken));
        assertNotNull(tokenManager.get(refreshToken));

        // 下线设备
        tokenManager.processRemoveToken(refreshToken);

        // 验证token已失效
        assertNull(tokenManager.getByAccessToken(accessToken), "下线后accessToken应失效");
        assertNull(tokenManager.get(refreshToken), "下线后refreshToken应失效");

        // 验证设备记录已清理
        assertNull(deviceManager.get(refreshToken), "下线后设备记录应清理");
        List<LoginDevice> devices = deviceManager.getByUserId(USER_ID);
        assertTrue(devices.isEmpty(), "下线后用户设备列表应为空");
    }

    @Test
    void testOfflineDeviceThenAccessFails() {
        // 创建token和设备
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);
        TokenContent tc = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome", System.currentTimeMillis(), System.currentTimeMillis()));

        String accessToken = tc.getAccessToken();
        String refreshToken = tc.getRefreshToken();

        // 下线设备
        tokenManager.processRemoveToken(refreshToken);

        // 再次使用被下线的accessToken访问 —— 应该失败
        TokenContent result = tokenManager.getByAccessToken(accessToken);
        assertNull(result, "被下线设备的accessToken再次访问应返回null");

        // 再次使用被下线的refreshToken刷新 —— 应该失败
        TokenContent refreshResult = tokenManager.get(refreshToken);
        assertNull(refreshResult, "被下线设备的refreshToken再次刷新应返回null");
    }

    @Test
    void testTgtInvalidationCleansAllDevices() {
        // 创建多个token（模拟多个设备）
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);

        TokenContent tc1 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc1.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc1.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome", System.currentTimeMillis(), System.currentTimeMillis()));

        TokenContent tc2 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc2.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc2.getRefreshToken(),
                "fp-002", "10.0.0.1", "Safari", System.currentTimeMillis(), System.currentTimeMillis()));

        // 验证有2个设备
        assertEquals(2, deviceManager.getByUserId(USER_ID).size());

        // TGT失效（模拟用户登出）
        tokenManager.removeByTgt(TGT);

        // 等待异步任务完成（processRemoveToken包含HTTP调用，可能较慢）
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证所有token和设备已清理
        assertNull(tokenManager.getByAccessToken(tc1.getAccessToken()));
        assertNull(tokenManager.getByAccessToken(tc2.getAccessToken()));
        assertTrue(deviceManager.getByUserId(USER_ID).isEmpty(), "TGT失效后所有设备应被清理");
    }

    @Test
    void testMultipleUsersIsolation() {
        Long user2 = 2001L;
        CodeContent codeContent = new CodeContent(TGT, CLIENT_ID);

        // 用户1创建设备
        TokenContent tc1 = tokenManager.create(USER_ID, LOGOUT_URI, codeContent);
        deviceManager.create(tc1.getRefreshToken(), new LoginDevice(USER_ID, CLIENT_ID, tc1.getRefreshToken(),
                "fp-001", "192.168.1.1", "Chrome", System.currentTimeMillis(), System.currentTimeMillis()));

        // 用户2创建设备
        TokenContent tc2 = tokenManager.create(user2, LOGOUT_URI, codeContent);
        deviceManager.create(tc2.getRefreshToken(), new LoginDevice(user2, CLIENT_ID, tc2.getRefreshToken(),
                "fp-002", "10.0.0.1", "Safari", System.currentTimeMillis(), System.currentTimeMillis()));

        // 下线用户1的设备
        tokenManager.processRemoveToken(tc1.getRefreshToken());

        // 用户2的设备不受影响
        assertNull(deviceManager.get(tc1.getRefreshToken()));
        assertNotNull(deviceManager.get(tc2.getRefreshToken()));
        assertEquals(0, deviceManager.getByUserId(USER_ID).size());
        assertEquals(1, deviceManager.getByUserId(user2).size());
    }
}
