package openjoe.smart.sso.server.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import openjoe.smart.sso.server.service.LoginDeviceService;
import openjoe.smart.stage.core.entity.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Joe
 */
@Tag(name = "登录设备管理")
@Controller
@RequestMapping("/admin/login-device")
@SuppressWarnings("rawtypes")
public class LoginDeviceController {

    @Autowired
    private LoginDeviceService loginDeviceService;

    @Operation(summary = "设备列表")
    @ResponseBody
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Result list(
            @RequestParam Long userId,
            @RequestParam Long current,
            @RequestParam Long size) {
        return Result.success(loginDeviceService.listByUserId(userId, current, size));
    }

    @Operation(summary = "单设备下线")
    @ResponseBody
    @RequestMapping(value = "/kickout", method = RequestMethod.POST)
    public Result kickOut(@RequestParam String deviceId) {
        loginDeviceService.kickOut(deviceId);
        return Result.success();
    }
}
