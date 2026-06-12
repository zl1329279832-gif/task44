package openjoe.smart.sso.server.manager;

import openjoe.smart.sso.base.entity.LifecycleManager;
import openjoe.smart.sso.server.entity.LoginDevice;

import java.util.List;

/**
 * 用户登录设备管理抽象
 *
 * @author Joe
 */
public abstract class AbstractDeviceManager implements LifecycleManager<LoginDevice> {

    /**
     * 根据refreshToken获取设备信息
     *
     * @param refreshToken 刷新凭证
     * @return 设备信息，不存在返回null
     */
    @Override
    public abstract LoginDevice get(String refreshToken);

    /**
     * 创建设备记录
     *
     * @param refreshToken 刷新凭证（作为存储key）
     * @param device       设备信息
     */
    @Override
    public abstract void create(String refreshToken, LoginDevice device);

    /**
     * 移除设备记录
     *
     * @param refreshToken 刷新凭证
     */
    @Override
    public abstract void remove(String refreshToken);

    /**
     * 根据用户ID获取所有登录设备
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    public abstract List<LoginDevice> getByUserId(Long userId);

    /**
     * 更新refreshToken（token刷新时调用）
     *
     * @param oldRefreshToken 旧的refreshToken
     * @param newRefreshToken 新的refreshToken
     * @param updateTime      更新时间戳（毫秒）
     */
    public abstract void updateRefreshToken(String oldRefreshToken, String newRefreshToken, long updateTime);

    /**
     * 移除用户所有登录设备
     *
     * @param userId 用户ID
     */
    public abstract void removeByUserId(Long userId);
}
