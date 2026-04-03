package openjoe.smart.sso.server.manager;

import openjoe.smart.sso.base.constant.BaseConstant;
import openjoe.smart.sso.base.entity.LifecycleManager;
import openjoe.smart.sso.base.entity.TokenUser;
import openjoe.smart.sso.base.util.HttpUtils;
import openjoe.smart.sso.server.entity.CodeContent;
import openjoe.smart.sso.server.entity.TokenContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 调用凭证AccessToken管理抽象
 *
 * @author Joe
 */
public abstract class AbstractTokenManager implements LifecycleManager<TokenContent> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * accessToken超时时效
     */
    private int accessTokenTimeout;

    /**
     * refreshToken时效和登录超时时效保持一致
     */
    private int refreshTokenTimeout;

    protected final ExecutorService executorService;

    public AbstractTokenManager(int accessTokenTimeout, int refreshTokenTimeout, int threadPoolSize) {
        this.accessTokenTimeout = accessTokenTimeout;
        this.refreshTokenTimeout = refreshTokenTimeout;
        //增加了命名和拒绝策略
        this.executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "token-manager-" + threadPoolSize);
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

    }

    /**
     * 通过AccessToken获取
     *
     * @param accessToken
     * @return
     */
    public abstract TokenContent getByAccessToken(String accessToken);

    /**
     * 通过TGT移除
     *
     * @param tgt
     */
    public abstract void removeByTgt(String tgt);

    /**
     * 创建AccessToken
     *
     * @param tc
     * @return
     */
    public TokenContent create(TokenContent tc) {
        return create(tc.getUserId(), tc.getLogoutUri(), tc);
    }

    /**
     * 创建AccessToken
     *
     * @param userId
     * @param codeContent
     * @return
     */
    public TokenContent create(Long userId, String logoutUri, CodeContent codeContent) {
        String accessToken = "AT-" + UUID.randomUUID().toString().replace("-", "");
        String refreshToken = "RT-" + UUID.randomUUID().toString().replace("-", "");
        TokenContent tc = new TokenContent(accessToken, refreshToken, userId, logoutUri, codeContent.getTgt(), codeContent.getClientId());
        create(refreshToken, tc);
        return tc;
    }

    /**
     * 提交删除Token任务
     *
     * @param refreshTokenSet
     */
    //提交到线程池的目的本来是异步并发执行，但用 future.get() 把调用线程完全阻塞住，等于所有子任务跑完才返回。
    //感觉这里这相当于用多线程做了单线程能做的事，还额外增加了线程调度的开销。
    //我是把这里改为了异步运行 让线程池后台处理
    protected void submitRemoveToken(Set<String> refreshTokenSet) {
        refreshTokenSet.forEach(refreshToken ->
                executorService.submit(() -> {
                    try {
                        processRemoveToken(refreshToken);
                    } catch (Exception e) {
                        logger.error("执行删除Token操作出现异常, refreshToken: {}", refreshToken, e);
                    }
                })
        );
    }


    /**
     * 真正执行删除Token
     *
     * @param refreshToken
     */
    public abstract void processRemoveToken(String refreshToken);

    public abstract Map<String, Set<String>> getClientIdMapByTgt(Set<String> tgtSet);

    /**
     * 发起客户端退出请求
     *
     * @param redirectUri
     * @param accessToken
     */
    protected void sendLogoutRequest(String redirectUri, String accessToken) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(BaseConstant.LOGOUT_PARAMETER_NAME, accessToken);
        HttpUtils.postHeader(redirectUri, headerMap);

        //失败记录
        String result = HttpUtils.postHeader(redirectUri, headerMap);
        if (result == null) {
            logger.warn("客户端退出通知失败, redirectUri: {}", redirectUri);
        }
    }

    public int getAccessTokenTimeout() {
        return accessTokenTimeout;
    }

    public void setAccessTokenTimeout(int accessTokenTimeout) {
        this.accessTokenTimeout = accessTokenTimeout;
    }

    public int getRefreshTokenTimeout() {
        return refreshTokenTimeout;
    }

    public void setRefreshTokenTimeout(int refreshTokenTimeout) {
        this.refreshTokenTimeout = refreshTokenTimeout;
    }
}
