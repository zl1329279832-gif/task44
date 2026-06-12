package openjoe.smart.sso.server.controller;

import openjoe.smart.sso.base.entity.Result;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.manager.AbstractDeviceManager;
import openjoe.smart.sso.server.manager.AbstractTokenManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户登录设备管理
 *
 * @author Joe
 */
@RestController
@RequestMapping("/admin/device")
public class DeviceController {

    @Autowired
    private AbstractDeviceManager deviceManager;
    @Autowired
    private AbstractTokenManager tokenManager;

    /**
     * 查询用户登录设备列表
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Result<List<LoginDevice>> list(@RequestParam Long userId) {
        List<LoginDevice> devices = deviceManager.getByUserId(userId);
        return Result.success(devices);
    }

    /**
     * 下线指定设备
     *
     * @param refreshToken 设备关联的refreshToken
     * @return 操作结果
     */
    @RequestMapping(value = "/offline", method = RequestMethod.POST)
    public Result<Void> offline(@RequestParam String refreshToken) {
        LoginDevice device = deviceManager.get(refreshToken);
        if (device == null) {
            return Result.error("设备不存在或已下线");
        }
        // processRemoveToken会：1.移除AT+RT 2.发送SSO退出通知 3.清理设备记录
        tokenManager.processRemoveToken(refreshToken);
        return Result.success();
    }

    /**
     * 下线用户所有设备
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @RequestMapping(value = "/offline-all", method = RequestMethod.POST)
    public Result<Void> offlineAll(@RequestParam Long userId) {
        List<LoginDevice> devices = deviceManager.getByUserId(userId);
        if (devices == null || devices.isEmpty()) {
            return Result.success();
        }
        for (LoginDevice device : devices) {
            tokenManager.processRemoveToken(device.getRefreshToken());
        }
        return Result.success();
    }
}
