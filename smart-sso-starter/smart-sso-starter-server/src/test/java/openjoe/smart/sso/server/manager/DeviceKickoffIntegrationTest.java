package openjoe.smart.sso.server.manager;

import openjoe.smart.sso.server.entity.CodeContent;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.entity.TokenContent;
import openjoe.smart.sso.server.manager.local.LocalLoginDeviceManager;
import openjoe.smart.sso.server.manager.local.LocalTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备下线集成测试（LocalTokenManager + LocalLoginDeviceManager联合测试）
 */
class DeviceKickoffIntegrationTest {

    private TestableLocalTokenManager tokenManager;
    private LocalLoginDeviceManager deviceManager;

    @BeforeEach
    void setUp() {
        deviceManager = new LocalLoginDeviceManager(7200);
        tokenManager = new TestableLocalTokenManager(1800, 7200, 2);
        tokenManager.setDeviceManager(deviceManager);
    }

    @Test
    void testKickDevice_invalidatesTokens() {
        // 创建token和设备记录
        CodeContent codeContent = new CodeContent("TGT-1", "app1");
        TokenContent tc = tokenManager.create(1L, "http://localhost/logout", codeContent, "device-1");

        long now = System.currentTimeMillis();
        LoginDeviceContent device = new LoginDeviceContent("device-1", "app1", 1L,
                "192.168.1.1", "Chrome", now, now, tc.getRefreshToken(), tc.getAccessToken(), "TGT-1");
        deviceManager.create("device-1", device);

        // 验证token和设备记录存在
        assertNotNull(tokenManager.getByAccessToken(tc.getAccessToken()));
        assertNotNull(deviceManager.get("device-1"));

        // 通过processRemoveToken踢下线
        tokenManager.processRemoveToken(tc.getRefreshToken());

        // 验证token已失效
        assertNull(tokenManager.getByAccessToken(tc.getAccessToken()));
        assertNull(tokenManager.get(tc.getRefreshToken()));

        // 验证设备记录已清理
        assertNull(deviceManager.get("device-1"));
    }

    @Test
    void testKickedDeviceAccessFailure() {
        // 创建token和设备
        CodeContent codeContent = new CodeContent("TGT-1", "app1");
        TokenContent tc = tokenManager.create(1L, "http://localhost/logout", codeContent, "device-1");
        String accessToken = tc.getAccessToken();

        long now = System.currentTimeMillis();
        LoginDeviceContent device = new LoginDeviceContent("device-1", "app1", 1L,
                "192.168.1.1", "Chrome", now, now, tc.getRefreshToken(), tc.getAccessToken(), "TGT-1");
        deviceManager.create("device-1", device);

        // 踢下线
        tokenManager.processRemoveToken(tc.getRefreshToken());

        // 被下线设备再次用accessToken访问应失败
        assertNull(tokenManager.getByAccessToken(accessToken));
    }

    @Test
    void testRefreshTokenUpdatesDeviceActiveTime() throws InterruptedException {
        // 创建token和设备
        CodeContent codeContent = new CodeContent("TGT-1", "app1");
        TokenContent tc = tokenManager.create(1L, "http://localhost/logout", codeContent, "device-1");

        long now = System.currentTimeMillis();
        LoginDeviceContent device = new LoginDeviceContent("device-1", "app1", 1L,
                "192.168.1.1", "Chrome", now, now, tc.getRefreshToken(), tc.getAccessToken(), "TGT-1");
        deviceManager.create("device-1", device);

        long loginTime = deviceManager.get("device-1").getLoginTime();

        // 等待一小段时间
        Thread.sleep(50);

        // 模拟refresh：删除旧token，创建新token，更新设备
        TokenContent oldTc = tokenManager.get(tc.getRefreshToken());
        tokenManager.remove(tc.getRefreshToken());
        TokenContent newTc = tokenManager.create(oldTc);

        // 更新设备活跃时间
        deviceManager.updateOnRefresh("device-1", newTc.getRefreshToken(), newTc.getAccessToken());

        LoginDeviceContent updatedDevice = deviceManager.get("device-1");
        assertNotNull(updatedDevice);
        assertEquals(newTc.getRefreshToken(), updatedDevice.getRefreshToken());
        assertEquals(newTc.getAccessToken(), updatedDevice.getAccessToken());
        assertTrue(updatedDevice.getLastRefreshTime() > loginTime);
        assertEquals(loginTime, updatedDevice.getLoginTime()); // 登录时间不变
    }

    @Test
    void testLogout_removesAllDevicesForTgt() {
        // 在同一个TGT下创建两个token+设备
        CodeContent code1 = new CodeContent("TGT-1", "app1");
        TokenContent tc1 = tokenManager.create(1L, "http://localhost/logout", code1, "device-1");

        CodeContent code2 = new CodeContent("TGT-1", "app2");
        TokenContent tc2 = tokenManager.create(1L, "http://localhost/logout", code2, "device-2");

        long now = System.currentTimeMillis();
        deviceManager.create("device-1", new LoginDeviceContent("device-1", "app1", 1L,
                "192.168.1.1", "Chrome", now, now, tc1.getRefreshToken(), tc1.getAccessToken(), "TGT-1"));
        deviceManager.create("device-2", new LoginDeviceContent("device-2", "app2", 1L,
                "192.168.1.2", "Firefox", now, now, tc2.getRefreshToken(), tc2.getAccessToken(), "TGT-1"));

        // 验证设备存在
        assertEquals(2, deviceManager.getByUserId(1L).size());

        // 通过TGT下线（模拟logout）
        tokenManager.removeByTgt("TGT-1");

        // 等待异步任务完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证所有设备记录已清理
        assertNull(deviceManager.get("device-1"));
        assertNull(deviceManager.get("device-2"));
    }

    /**
     * 测试用LocalTokenManager子类，覆盖sendLogoutRequest避免真实HTTP调用
     */
    static class TestableLocalTokenManager extends LocalTokenManager {
        public TestableLocalTokenManager(int accessTokenTimeout, int refreshTokenTimeout, int threadPoolSize) {
            super(accessTokenTimeout, refreshTokenTimeout, threadPoolSize);
        }

        @Override
        protected void sendLogoutRequest(String redirectUri, String accessToken) {
            // 测试中不发起HTTP请求
        }
    }
}
