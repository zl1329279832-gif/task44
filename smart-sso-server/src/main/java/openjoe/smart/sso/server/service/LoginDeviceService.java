package openjoe.smart.sso.server.service;

import openjoe.smart.sso.server.dto.LoginDeviceDTO;
import openjoe.smart.stage.core.entity.Page;

/**
 * 登录设备服务接口
 *
 * @author Joe
 */
public interface LoginDeviceService {

    /**
     * 查询用户的登录设备分页列表
     *
     * @param userId
     * @param current
     * @param size
     * @return
     */
    Page<LoginDeviceDTO> listByUserId(Long userId, Long current, Long size);

    /**
     * 踢下线指定设备
     *
     * @param deviceId
     */
    void kickOut(String deviceId);
}
