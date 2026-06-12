package openjoe.smart.sso.server.manager.local;

import openjoe.smart.sso.base.entity.ExpirationPolicy;
import openjoe.smart.sso.base.entity.ExpirationWrapper;
import openjoe.smart.sso.server.entity.TokenContent;
import openjoe.smart.sso.server.manager.AbstractDeviceManager;
import openjoe.smart.sso.server.manager.AbstractTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地调用凭证管理
 *
 * @author Joe
 */
public class LocalTokenManager extends AbstractTokenManager implements ExpirationPolicy {

    private final Logger logger = LoggerFactory.getLogger(LocalTokenManager.class);
    private final Map<String, ExpirationWrapper<String>> accessTokenMap = new ConcurrentHashMap<>();
    private final Map<String, ExpirationWrapper<TokenContent>> refreshTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tgtMap = new ConcurrentHashMap<>();

    public LocalTokenManager(int accessTokenTimeout, int refreshTokenTimeout, int threadPoolSize) {
        super(accessTokenTimeout, refreshTokenTimeout, threadPoolSize);
    }

    public LocalTokenManager(int accessTokenTimeout, int refreshTokenTimeout, int threadPoolSize, AbstractDeviceManager deviceManager) {
        super(accessTokenTimeout, refreshTokenTimeout, threadPoolSize, deviceManager);
    }

    @Override
    public void create(String refreshToken, TokenContent tokenContent) {
        ExpirationWrapper<String> atWrapper = new ExpirationWrapper(refreshToken, getAccessTokenTimeout());
        accessTokenMap.put(tokenContent.getAccessToken(), atWrapper);

        ExpirationWrapper<TokenContent> rtWrapper = new ExpirationWrapper(tokenContent, getRefreshTokenTimeout());
        refreshTokenMap.put(refreshToken, rtWrapper);

        tgtMap.computeIfAbsent(tokenContent.getTgt(), a -> ConcurrentHashMap.newKeySet()).add(refreshToken);
        logger.debug("调用凭证创建成功, accessToken:{}, refreshToken:{}", tokenContent.getAccessToken(), refreshToken);
    }

    @Override
    public TokenContent get(String refreshToken) {
        ExpirationWrapper<TokenContent> wrapper = refreshTokenMap.get(refreshToken);
        if (wrapper == null || wrapper.checkExpired()) {
            return null;
        } else {
            return wrapper.getObject();
        }
    }

    @Override
    public TokenContent getByAccessToken(String accessToken) {
        ExpirationWrapper<String> wrapper = accessTokenMap.get(accessToken);
        if (wrapper == null || wrapper.checkExpired()) {
            return null;
        }
        return get(wrapper.getObject());
    }

    @Override
    public void remove(String refreshToken) {
        // 删除refreshToken
        ExpirationWrapper<TokenContent> wrapper = refreshTokenMap.remove(refreshToken);
        if (wrapper == null) {
            return;
        }

        // 删除accessToken
        accessTokenMap.remove(wrapper.getObject().getAccessToken());

        // 删除tgt映射中的refreshToken
        Set<String> refreshTokenSet = tgtMap.get(wrapper.getObject().getTgt());
        if (!CollectionUtils.isEmpty(refreshTokenSet)) {
            refreshTokenSet.remove(refreshToken);
        }

        // 清理设备记录
        removeDevice(refreshToken);
    }

    @Override
    public TokenContent consumeRefreshToken(String refreshToken) {
        // ConcurrentHashMap.remove是原子操作，确保只有一个线程能成功消费同一refreshToken
        ExpirationWrapper<TokenContent> wrapper = refreshTokenMap.remove(refreshToken);
        if (wrapper == null || wrapper.checkExpired()) {
            return null;
        }
        TokenContent tc = wrapper.getObject();
        if (tc == null) {
            return null;
        }
        // 移除accessToken
        accessTokenMap.remove(tc.getAccessToken());
        // 移除tgt映射中的refreshToken
        Set<String> rtSet = tgtMap.get(tc.getTgt());
        if (!CollectionUtils.isEmpty(rtSet)) {
            rtSet.remove(refreshToken);
        }
        // 不清理设备记录 —— 由调用方通过updateRefreshToken迁移到新RT
        return tc;
    }

    @Override
    public void removeByTgt(String tgt) {
        // 删除tgt映射中的refreshToken集合
        Set<String> refreshTokenSet = tgtMap.remove(tgt);
        if (CollectionUtils.isEmpty(refreshTokenSet)) {
            return;
        }
        submitRemoveToken(refreshTokenSet);
    }

    @Override
    public void processRemoveToken(String refreshToken) {
        // 先移除refreshToken，防止并发刷新在设备记录清理期间成功
        ExpirationWrapper<TokenContent> wrapper = refreshTokenMap.remove(refreshToken);

        // 清理设备记录
        removeDevice(refreshToken);

        if (wrapper == null) {
            return;
        }
        TokenContent tokenContent = wrapper.getObject();
        if (tokenContent == null) {
            return;
        }

        // 删除accessToken
        accessTokenMap.remove(tokenContent.getAccessToken());

        // 发起客户端退出请求
        logger.debug("发起客户端退出请求, accessToken:{}, refreshToken:{}, logoutUri:{}", tokenContent.getAccessToken(), refreshToken, tokenContent.getLogoutUri());
        sendLogoutRequest(tokenContent.getLogoutUri(), tokenContent.getAccessToken());
    }

    @Override
    public Map<String, Set<String>> getClientIdMapByTgt(Set<String> tgtSet) {
        Map<String, Set<String>> clientIdMap = new HashMap<>();
        tgtSet.forEach(tgt -> {
            Set<String> refreshTokenSet = tgtMap.get(tgt);
            Set<String> clientIdSet = new HashSet<>();
            refreshTokenSet.forEach(refreshToken -> {
                ExpirationWrapper<TokenContent> wrapper = refreshTokenMap.get(refreshToken);
                if (wrapper == null) {
                    return;
                }
                TokenContent tokenContent = wrapper.getObject();
                if (tokenContent == null) {
                    return;
                }
                clientIdSet.add(tokenContent.getClientId());
            });
            clientIdMap.put(tgt, clientIdSet);
        });
        return clientIdMap;
    }

    @Override
    public void verifyExpired() {
        accessTokenMap.forEach((accessToken, wrapper) -> {
            if (wrapper.checkExpired()) {
                accessTokenMap.remove(accessToken);
                logger.debug("调用凭证已失效, accessToken:{}", accessToken);
            }
        });

        refreshTokenMap.forEach((refreshToken, wrapper) -> {
            if (wrapper.checkExpired()) {
                remove(refreshToken);
                logger.debug("刷新凭证已失效, accessToken:{}, refreshToken:{}", wrapper.getObject().getAccessToken(), refreshToken);
            }
        });
    }
}
