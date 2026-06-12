package openjoe.smart.sso.server.manager.redis;

import openjoe.smart.sso.base.util.JsonUtils;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.manager.AbstractLoginDeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分布式登录设备管理
 *
 * @author Joe
 */
public class RedisLoginDeviceManager extends AbstractLoginDeviceManager {

    private final Logger logger = LoggerFactory.getLogger(RedisLoginDeviceManager.class);
    private static final String DEVICE_KEY = "server_device_";
    private static final String USER_DEVICE_KEY = "server_device_user_";

    private StringRedisTemplate redisTemplate;

    public RedisLoginDeviceManager(int timeout, StringRedisTemplate redisTemplate) {
        super(timeout);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void create(String deviceId, LoginDeviceContent content) {
        redisTemplate.opsForValue().set(DEVICE_KEY + deviceId, JsonUtils.toString(content), getTimeout(), TimeUnit.SECONDS);
        redisTemplate.opsForSet().add(USER_DEVICE_KEY + content.getUserId(), deviceId);
        redisTemplate.expire(USER_DEVICE_KEY + content.getUserId(), getTimeout(), TimeUnit.SECONDS);
        logger.debug("Redis登录设备记录创建成功, deviceId:{}, userId:{}", deviceId, content.getUserId());
    }

    @Override
    public LoginDeviceContent get(String deviceId) {
        String content = redisTemplate.opsForValue().get(DEVICE_KEY + deviceId);
        if (!StringUtils.hasLength(content)) {
            return null;
        }
        return JsonUtils.parseObject(content, LoginDeviceContent.class);
    }

    @Override
    public void remove(String deviceId) {
        String content = redisTemplate.opsForValue().get(DEVICE_KEY + deviceId);
        if (!StringUtils.hasLength(content)) {
            return;
        }
        redisTemplate.delete(DEVICE_KEY + deviceId);

        LoginDeviceContent deviceContent = JsonUtils.parseObject(content, LoginDeviceContent.class);
        if (deviceContent != null) {
            redisTemplate.opsForSet().remove(USER_DEVICE_KEY + deviceContent.getUserId(), deviceId);
        }
        logger.debug("Redis登录设备记录删除成功, deviceId:{}", deviceId);
    }

    @Override
    public List<LoginDeviceContent> getByUserId(Long userId) {
        Set<String> deviceIds = redisTemplate.opsForSet().members(USER_DEVICE_KEY + userId);
        if (CollectionUtils.isEmpty(deviceIds)) {
            return Collections.emptyList();
        }
        return deviceIds.stream()
                .map(this::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void updateOnRefresh(String deviceId, String newRefreshToken, String newAccessToken) {
        LoginDeviceContent content = get(deviceId);
        if (content == null) {
            return;
        }
        content.setRefreshToken(newRefreshToken);
        content.setAccessToken(newAccessToken);
        content.setLastRefreshTime(System.currentTimeMillis());
        redisTemplate.opsForValue().set(DEVICE_KEY + deviceId, JsonUtils.toString(content), getTimeout(), TimeUnit.SECONDS);
        redisTemplate.expire(USER_DEVICE_KEY + content.getUserId(), getTimeout(), TimeUnit.SECONDS);
        logger.debug("Redis登录设备活跃时间更新成功, deviceId:{}", deviceId);
    }

    @Override
    public void removeByUserId(Long userId) {
        Set<String> deviceIds = redisTemplate.opsForSet().members(USER_DEVICE_KEY + userId);
        if (CollectionUtils.isEmpty(deviceIds)) {
            return;
        }
        deviceIds.forEach(deviceId -> redisTemplate.delete(DEVICE_KEY + deviceId));
        redisTemplate.delete(USER_DEVICE_KEY + userId);
        logger.debug("Redis用户所有登录设备删除成功, userId:{}", userId);
    }
}
