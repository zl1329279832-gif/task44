package openjoe.smart.sso.server.service.impl;

import openjoe.smart.sso.server.dto.LoginDeviceDTO;
import openjoe.smart.sso.server.entity.App;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.entity.User;
import openjoe.smart.sso.server.manager.AbstractLoginDeviceManager;
import openjoe.smart.sso.server.manager.AbstractTokenManager;
import openjoe.smart.sso.server.service.AppService;
import openjoe.smart.sso.server.service.LoginDeviceService;
import openjoe.smart.sso.server.service.UserService;
import openjoe.smart.stage.core.entity.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("loginDeviceService")
public class LoginDeviceServiceImpl implements LoginDeviceService {

    @Autowired
    private AbstractLoginDeviceManager deviceManager;
    @Autowired
    private AbstractTokenManager tokenManager;
    @Autowired
    private UserService userService;
    @Autowired
    private AppService appService;

    @Override
    public Page<LoginDeviceDTO> listByUserId(Long userId, Long current, Long size) {
        List<LoginDeviceContent> devices = deviceManager.getByUserId(userId);
        List<LoginDeviceDTO> dtoList = convertList(devices, userId);

        // 内存分页
        long start = (current - 1) * size;
        long end = Math.min(start + size, dtoList.size());
        List<LoginDeviceDTO> pageList;
        if (start >= dtoList.size()) {
            pageList = Collections.emptyList();
        } else {
            pageList = dtoList.subList((int) start, (int) end);
        }
        return Page.of(current, size, pageList);
    }

    private List<LoginDeviceDTO> convertList(List<LoginDeviceContent> devices, Long userId) {
        if (devices == null || devices.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询用户信息
        Set<Long> userIds = devices.stream().map(LoginDeviceContent::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.selectMapByIds(userIds);

        // 批量查询应用信息
        Set<String> clientIds = devices.stream().map(LoginDeviceContent::getClientId).collect(Collectors.toSet());
        Map<String, App> appMap = appService.selectMapByClientIds(clientIds);

        List<LoginDeviceDTO> dtoList = new ArrayList<>();
        for (LoginDeviceContent device : devices) {
            LoginDeviceDTO dto = new LoginDeviceDTO();
            dto.setDeviceId(device.getDeviceId());
            dto.setClientId(device.getClientId());
            dto.setUserId(device.getUserId());
            dto.setIp(device.getIp());
            dto.setUserAgent(device.getUserAgent());
            dto.setLoginTime(new Date(device.getLoginTime()));
            dto.setLastRefreshTime(new Date(device.getLastRefreshTime()));

            User user = userMap.get(device.getUserId());
            if (user != null) {
                dto.setUsername(user.getName());
            }

            App app = appMap.get(device.getClientId());
            if (app != null) {
                dto.setAppName(app.getCode());
            }

            dtoList.add(dto);
        }
        return dtoList;
    }

    @Override
    public void kickOut(String deviceId) {
        LoginDeviceContent device = deviceManager.get(deviceId);
        if (device == null) {
            return;
        }
        // 通过processRemoveToken删除Token、发送退出通知、并通过onTokenRemoved清理设备记录
        tokenManager.processRemoveToken(device.getRefreshToken());
    }
}
