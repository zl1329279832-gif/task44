package openjoe.smart.sso.server.manager.redis;

import openjoe.smart.sso.base.util.JsonUtils;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.manager.AbstractDeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式登录设备管理
 *
 * @author Joe
 */
public class RedisDeviceManager extends AbstractDeviceManager {

    private static final Logger logger = LoggerFactory.getLogger(RedisDeviceManager.class);
    private static final String DEVICE_RT_KEY = "server_device_rt_";
    private static final String DEVICE_USER_KEY = "server_device_user_";

    private final int timeout;
    private final StringRedisTemplate redisTemplate;

    public RedisDeviceManager(int timeout, StringRedisTemplate redisTemplate) {
        this.timeout = timeout;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void create(String refreshToken, LoginDevice device) {
        redisTemplate.opsForValue().set(DEVICE_RT_KEY + refreshToken, JsonUtils.toString(device),
                timeout, TimeUnit.SECONDS);
        redisTemplate.opsForSet().add(DEVICE_USER_KEY + device.getUserId(), refreshToken);
        redisTemplate.expire(DEVICE_USER_KEY + device.getUserId(), timeout, TimeUnit.SECONDS);
        logger.debug("Redis登录设备记录创建成功, refreshToken:{}, userId:{}", refreshToken, device.getUserId());
    }

    @Override
    public LoginDevice get(String refreshToken) {
        String json = redisTemplate.opsForValue().get(DEVICE_RT_KEY + refreshToken);
        if (!StringUtils.hasLength(json)) {
            return null;
        }
        return JsonUtils.parseObject(json, LoginDevice.class);
    }

    @Override
    public void remove(String refreshToken) {
        String json = redisTemplate.opsForValue().get(DEVICE_RT_KEY + refreshToken);
        if (!StringUtils.hasLength(json)) {
            return;
        }
        redisTemplate.delete(DEVICE_RT_KEY + refreshToken);

        LoginDevice device = JsonUtils.parseObject(json, LoginDevice.class);
        if (device != null) {
            redisTemplate.opsForSet().remove(DEVICE_USER_KEY + device.getUserId(), refreshToken);
        }
        logger.debug("Redis登录设备记录移除, refreshToken:{}", refreshToken);
    }

    @Override
    public List<LoginDevice> getByUserId(Long userId) {
        Set<String> rtSet = redisTemplate.opsForSet().members(DEVICE_USER_KEY + userId);
        if (CollectionUtils.isEmpty(rtSet)) {
            return Collections.emptyList();
        }
        List<LoginDevice> result = new ArrayList<>();
        for (String rt : rtSet) {
            String json = redisTemplate.opsForValue().get(DEVICE_RT_KEY + rt);
            if (StringUtils.hasLength(json)) {
                result.add(JsonUtils.parseObject(json, LoginDevice.class));
            } else {
                // 清理过期引用
                redisTemplate.opsForSet().remove(DEVICE_USER_KEY + userId, rt);
            }
        }
        return result;
    }

    @Override
    public boolean updateRefreshToken(String oldRefreshToken, String newRefreshToken, long updateTime) {
        String json = redisTemplate.opsForValue().get(DEVICE_RT_KEY + oldRefreshToken);
        if (!StringUtils.hasLength(json)) {
            return false;
        }
        // 删除旧记录
        redisTemplate.delete(DEVICE_RT_KEY + oldRefreshToken);

        LoginDevice device = JsonUtils.parseObject(json, LoginDevice.class);
        if (device == null) {
            return false;
        }

        // 更新设备信息
        device.setRefreshToken(newRefreshToken);
        device.setLastRefreshTime(updateTime);
        redisTemplate.opsForValue().set(DEVICE_RT_KEY + newRefreshToken, JsonUtils.toString(device),
                timeout, TimeUnit.SECONDS);

        // 更新用户索引
        redisTemplate.opsForSet().remove(DEVICE_USER_KEY + device.getUserId(), oldRefreshToken);
        redisTemplate.opsForSet().add(DEVICE_USER_KEY + device.getUserId(), newRefreshToken);
        redisTemplate.expire(DEVICE_USER_KEY + device.getUserId(), timeout, TimeUnit.SECONDS);

        logger.debug("Redis设备refreshToken更新, old:{}, new:{}", oldRefreshToken, newRefreshToken);
        return true;
    }

    @Override
    public void removeByUserId(Long userId) {
        Set<String> rtSet = redisTemplate.opsForSet().members(DEVICE_USER_KEY + userId);
        if (CollectionUtils.isEmpty(rtSet)) {
            return;
        }
        for (String rt : rtSet) {
            redisTemplate.delete(DEVICE_RT_KEY + rt);
        }
        redisTemplate.delete(DEVICE_USER_KEY + userId);
        logger.debug("Redis用户所有登录设备移除, userId:{}", userId);
    }
}
