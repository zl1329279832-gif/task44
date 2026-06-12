package openjoe.smart.sso.server.entity;

/**
 * 登录设备存储信息
 *
 * @author Joe
 */
public class LoginDeviceContent {

    private String deviceId;
    private String clientId;
    private Long userId;
    private String ip;
    private String userAgent;
    private Long loginTime;
    private Long lastRefreshTime;
    private String refreshToken;
    private String accessToken;
    private String tgt;

    public LoginDeviceContent() {
    }

    public LoginDeviceContent(String deviceId, String clientId, Long userId, String ip, String userAgent,
                              Long loginTime, Long lastRefreshTime, String refreshToken, String accessToken, String tgt) {
        this.deviceId = deviceId;
        this.clientId = clientId;
        this.userId = userId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.loginTime = loginTime;
        this.lastRefreshTime = lastRefreshTime;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.tgt = tgt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTgt() {
        return tgt;
    }

    public void setTgt(String tgt) {
        this.tgt = tgt;
    }
}
