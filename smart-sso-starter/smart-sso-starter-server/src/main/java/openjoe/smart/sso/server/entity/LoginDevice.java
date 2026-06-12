package openjoe.smart.sso.server.entity;

/**
 * 用户登录设备信息
 *
 * @author Joe
 */
public class LoginDevice {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 应用ID
     */
    private String clientId;

    /**
     * 刷新凭证（关联Token）
     */
    private String refreshToken;

    /**
     * 设备指纹
     */
    private String deviceFingerprint;

    /**
     * 登录IP
     */
    private String ip;

    /**
     * 浏览器User-Agent
     */
    private String userAgent;

    /**
     * 登录时间（毫秒时间戳）
     */
    private Long loginTime;

    /**
     * 最后刷新时间（毫秒时间戳）
     */
    private Long lastRefreshTime;

    public LoginDevice() {
    }

    public LoginDevice(Long userId, String clientId, String refreshToken, String deviceFingerprint,
                       String ip, String userAgent, Long loginTime, Long lastRefreshTime) {
        this.userId = userId;
        this.clientId = clientId;
        this.refreshToken = refreshToken;
        this.deviceFingerprint = deviceFingerprint;
        this.ip = ip;
        this.userAgent = userAgent;
        this.loginTime = loginTime;
        this.lastRefreshTime = lastRefreshTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Long loginTime) {
        this.loginTime = loginTime;
    }

    public Long getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(Long lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }
}
