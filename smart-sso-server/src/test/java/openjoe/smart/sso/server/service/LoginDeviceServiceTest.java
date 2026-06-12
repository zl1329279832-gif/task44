package openjoe.smart.sso.server.service;

import openjoe.smart.sso.server.dto.LoginDeviceDTO;
import openjoe.smart.sso.server.entity.App;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.entity.User;
import openjoe.smart.sso.server.manager.AbstractLoginDeviceManager;
import openjoe.smart.sso.server.manager.AbstractTokenManager;
import openjoe.smart.sso.server.service.impl.LoginDeviceServiceImpl;
import openjoe.smart.stage.core.entity.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 登录设备服务测试
 */
@ExtendWith(MockitoExtension.class)
class LoginDeviceServiceTest {

    @Mock
    private AbstractLoginDeviceManager deviceManager;
    @Mock
    private AbstractTokenManager tokenManager;
    @Mock
    private UserService userService;
    @Mock
    private AppService appService;

    @InjectMocks
    private LoginDeviceServiceImpl loginDeviceService;

    private LoginDeviceContent device1;
    private LoginDeviceContent device2;

    @BeforeEach
    void setUp() {
        long now = System.currentTimeMillis();
        device1 = new LoginDeviceContent("device-1", "client-1", 1L,
                "192.168.1.1", "Chrome", now, now, "RT-1", "AT-1", "TGT-1");
        device2 = new LoginDeviceContent("device-2", "client-2", 1L,
                "192.168.1.2", "Firefox", now, now + 100, "RT-2", "AT-2", "TGT-1");
    }

    @Test
    void testListByUserId_returnsPaginatedDevices() {
        // mock设备列表
        when(deviceManager.getByUserId(1L)).thenReturn(Arrays.asList(device1, device2));

        // mock用户信息
        User user = new User();
        user.setId(1L);
        user.setName("testuser");
        Map<Long, User> userMap = new HashMap<>();
        userMap.put(1L, user);
        when(userService.selectMapByIds(anySet())).thenReturn(userMap);

        // mock应用信息
        App app1 = new App();
        app1.setClientId("client-1");
        app1.setCode("应用1");
        App app2 = new App();
        app2.setClientId("client-2");
        app2.setCode("应用2");
        Map<String, App> appMap = new HashMap<>();
        appMap.put("client-1", app1);
        appMap.put("client-2", app2);
        when(appService.selectMapByClientIds(anyCollection())).thenReturn(appMap);

        // 执行查询
        Page<LoginDeviceDTO> page = loginDeviceService.listByUserId(1L, 1L, 10L);

        assertNotNull(page);
        List<LoginDeviceDTO> list = page.getRecords();
        assertEquals(2, list.size());

        LoginDeviceDTO dto1 = list.get(0);
        assertEquals("device-1", dto1.getDeviceId());
        assertEquals("client-1", dto1.getClientId());
        assertEquals("testuser", dto1.getUsername());
        assertEquals("应用1", dto1.getAppName());
        assertEquals("192.168.1.1", dto1.getIp());
    }

    @Test
    void testKickOut_callsProcessRemoveToken() {
        when(deviceManager.get("device-1")).thenReturn(device1);

        loginDeviceService.kickOut("device-1");

        verify(tokenManager).processRemoveToken("RT-1");
    }

    @Test
    void testKickOut_nonExistentDevice() {
        when(deviceManager.get("no-such-device")).thenReturn(null);

        // 不应抛异常
        assertDoesNotThrow(() -> loginDeviceService.kickOut("no-such-device"));

        verify(tokenManager, never()).processRemoveToken(anyString());
    }
}
