package openjoe.smart.sso.server.manager.local;

import openjoe.smart.sso.base.entity.ExpirationPolicy;
import openjoe.smart.sso.base.entity.ExpirationWrapper;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.manager.AbstractLoginDeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地登录设备管理
 *
 * @author Joe
 */
public class LocalLoginDeviceManager extends AbstractLoginDeviceManager implements ExpirationPolicy {

    private final Logger logger = LoggerFactory.getLogger(LocalLoginDeviceManager.class);
    private final Map<String, ExpirationWrapper<LoginDeviceContent>> deviceMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userDeviceMap = new ConcurrentHashMap<>();

    public LocalLoginDeviceManager(int timeout) {
        super(timeout);
    }

    @Override
    public void create(String deviceId, LoginDeviceContent content) {
        ExpirationWrapper<LoginDeviceContent> wrapper = new ExpirationWrapper<>(content, getTimeout());
        deviceMap.put(deviceId, wrapper);
        userDeviceMap.computeIfAbsent(content.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(deviceId);
        logger.debug("登录设备记录创建成功, deviceId:{}, userId:{}", deviceId, content.getUserId());
    }

    @Override
    public LoginDeviceContent get(String deviceId) {
        ExpirationWrapper<LoginDeviceContent> wrapper = deviceMap.get(deviceId);
        if (wrapper == null || wrapper.checkExpired()) {
            return null;
        }
        return wrapper.getObject();
    }

    @Override
    public void remove(String deviceId) {
        ExpirationWrapper<LoginDeviceContent> wrapper = deviceMap.remove(deviceId);
        if (wrapper == null) {
            return;
        }
        LoginDeviceContent content = wrapper.getObject();
        if (content != null) {
            Set<String> deviceIds = userDeviceMap.get(content.getUserId());
            if (deviceIds != null) {
                deviceIds.remove(deviceId);
                if (deviceIds.isEmpty()) {
                    userDeviceMap.remove(content.getUserId());
                }
            }
        }
        logger.debug("登录设备记录删除成功, deviceId:{}", deviceId);
    }

    @Override
    public List<LoginDeviceContent> getByUserId(Long userId) {
        Set<String> deviceIds = userDeviceMap.get(userId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return deviceIds.stream()
                .map(this::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void updateOnRefresh(String deviceId, String newRefreshToken, String newAccessToken) {
        ExpirationWrapper<LoginDeviceContent> wrapper = deviceMap.get(deviceId);
        if (wrapper == null || wrapper.checkExpired()) {
            return;
        }
        LoginDeviceContent content = wrapper.getObject();
        content.setRefreshToken(newRefreshToken);
        content.setAccessToken(newAccessToken);
        content.setLastRefreshTime(System.currentTimeMillis());
        // 重置过期时间
        ExpirationWrapper<LoginDeviceContent> newWrapper = new ExpirationWrapper<>(content, getTimeout());
        deviceMap.put(deviceId, newWrapper);
        logger.debug("登录设备活跃时间更新成功, deviceId:{}", deviceId);
    }

    @Override
    public void removeByUserId(Long userId) {
        Set<String> deviceIds = userDeviceMap.remove(userId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        deviceIds.forEach(deviceMap::remove);
        logger.debug("用户所有登录设备删除成功, userId:{}", userId);
    }

    @Override
    public void verifyExpired() {
        deviceMap.forEach((deviceId, wrapper) -> {
            if (wrapper.checkExpired()) {
                remove(deviceId);
                logger.debug("登录设备记录已失效, deviceId:{}", deviceId);
            }
        });
    }
}
