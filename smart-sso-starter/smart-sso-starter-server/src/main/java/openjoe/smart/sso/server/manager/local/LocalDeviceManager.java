package openjoe.smart.sso.server.manager.local;

import openjoe.smart.sso.base.entity.ExpirationPolicy;
import openjoe.smart.sso.base.entity.ExpirationWrapper;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.manager.AbstractDeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地登录设备管理
 *
 * @author Joe
 */
public class LocalDeviceManager extends AbstractDeviceManager implements ExpirationPolicy {

    private static final Logger logger = LoggerFactory.getLogger(LocalDeviceManager.class);

    /**
     * refreshToken -> 设备信息
     */
    private final Map<String, ExpirationWrapper<LoginDevice>> deviceMap = new ConcurrentHashMap<>();

    /**
     * userId -> refreshToken集合
     */
    private final Map<Long, Set<String>> userDeviceMap = new ConcurrentHashMap<>();

    /**
     * 设备超时时间（与refreshToken一致）
     */
    private int timeout;

    public LocalDeviceManager(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void create(String refreshToken, LoginDevice device) {
        ExpirationWrapper<LoginDevice> wrapper = new ExpirationWrapper<>(device, timeout);
        deviceMap.put(refreshToken, wrapper);
        userDeviceMap.computeIfAbsent(device.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(refreshToken);
        logger.debug("登录设备记录创建成功, refreshToken:{}, userId:{}", refreshToken, device.getUserId());
    }

    @Override
    public LoginDevice get(String refreshToken) {
        ExpirationWrapper<LoginDevice> wrapper = deviceMap.get(refreshToken);
        if (wrapper == null || wrapper.checkExpired()) {
            return null;
        }
        return wrapper.getObject();
    }

    @Override
    public void remove(String refreshToken) {
        ExpirationWrapper<LoginDevice> wrapper = deviceMap.remove(refreshToken);
        if (wrapper == null) {
            return;
        }
        LoginDevice device = wrapper.getObject();
        if (device != null) {
            Set<String> rtSet = userDeviceMap.get(device.getUserId());
            if (rtSet != null) {
                rtSet.remove(refreshToken);
                if (rtSet.isEmpty()) {
                    userDeviceMap.remove(device.getUserId());
                }
            }
        }
        logger.debug("登录设备记录移除, refreshToken:{}", refreshToken);
    }

    @Override
    public List<LoginDevice> getByUserId(Long userId) {
        Set<String> rtSet = userDeviceMap.get(userId);
        if (CollectionUtils.isEmpty(rtSet)) {
            return Collections.emptyList();
        }
        List<LoginDevice> result = new ArrayList<>();
        List<String> staleEntries = new ArrayList<>();
        for (String rt : rtSet) {
            ExpirationWrapper<LoginDevice> wrapper = deviceMap.get(rt);
            if (wrapper != null && !wrapper.checkExpired()) {
                result.add(wrapper.getObject());
            } else {
                // 清理过期或已失效的引用，与Redis实现保持一致
                staleEntries.add(rt);
            }
        }
        staleEntries.forEach(rtSet::remove);
        if (rtSet.isEmpty()) {
            userDeviceMap.remove(userId);
        }
        return result;
    }

    @Override
    public void updateRefreshToken(String oldRefreshToken, String newRefreshToken, long updateTime) {
        ExpirationWrapper<LoginDevice> wrapper = deviceMap.remove(oldRefreshToken);
        if (wrapper == null) {
            return;
        }
        LoginDevice device = wrapper.getObject();
        if (device == null) {
            return;
        }
        device.setRefreshToken(newRefreshToken);
        device.setLastRefreshTime(updateTime);
        ExpirationWrapper<LoginDevice> newWrapper = new ExpirationWrapper<>(device, timeout);
        deviceMap.put(newRefreshToken, newWrapper);

        // 更新用户索引
        Set<String> rtSet = userDeviceMap.get(device.getUserId());
        if (rtSet != null) {
            rtSet.remove(oldRefreshToken);
            rtSet.add(newRefreshToken);
        }
        logger.debug("设备refreshToken更新, old:{}, new:{}", oldRefreshToken, newRefreshToken);
    }

    @Override
    public void removeByUserId(Long userId) {
        Set<String> rtSet = userDeviceMap.remove(userId);
        if (CollectionUtils.isEmpty(rtSet)) {
            return;
        }
        for (String rt : rtSet) {
            deviceMap.remove(rt);
        }
        logger.debug("用户所有登录设备移除, userId:{}", userId);
    }

    @Override
    public void verifyExpired() {
        deviceMap.forEach((refreshToken, wrapper) -> {
            if (wrapper.checkExpired()) {
                remove(refreshToken);
                logger.debug("登录设备记录已过期, refreshToken:{}", refreshToken);
            }
        });
    }
}
