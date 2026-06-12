package openjoe.smart.sso.server.manager;

import openjoe.smart.sso.base.entity.LifecycleManager;
import openjoe.smart.sso.server.entity.LoginDeviceContent;

import java.util.List;

/**
 * 登录设备管理抽象
 *
 * @author Joe
 */
public abstract class AbstractLoginDeviceManager implements LifecycleManager<LoginDeviceContent> {

    private int timeout;

    public AbstractLoginDeviceManager(int timeout) {
        this.timeout = timeout;
    }

    /**
     * 根据用户ID获取所有登录设备
     *
     * @param userId
     * @return
     */
    public abstract List<LoginDeviceContent> getByUserId(Long userId);

    /**
     * 刷新Token后更新设备活跃时间和Token信息
     *
     * @param deviceId
     * @param newRefreshToken
     * @param newAccessToken
     */
    public abstract void updateOnRefresh(String deviceId, String newRefreshToken, String newAccessToken);

    /**
     * 根据用户ID删除所有登录设备
     *
     * @param userId
     */
    public abstract void removeByUserId(Long userId);

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
