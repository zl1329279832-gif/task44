package openjoe.smart.sso.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import openjoe.smart.sso.base.constant.BaseConstant;
import openjoe.smart.sso.base.entity.Result;
import openjoe.smart.sso.base.entity.Token;
import openjoe.smart.sso.base.entity.TokenUser;
import openjoe.smart.sso.base.enums.GrantTypeEnum;
import openjoe.smart.sso.server.entity.CodeContent;
import openjoe.smart.sso.server.entity.LoginDevice;
import openjoe.smart.sso.server.entity.TicketGrantingTicketContent;
import openjoe.smart.sso.server.entity.TokenContent;
import openjoe.smart.sso.server.manager.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2服务管理
 *
 * @author Joe
 */
@RestController
@RequestMapping(BaseConstant.AUTH_PATH)
public class SSOOAuth2Controller {

    private static final String DEVICE_FINGERPRINT_HEADER = "X-Device-Fingerprint";

    @Autowired
    private AppManager appManager;
    @Autowired
    private UserManager userManager;
    @Autowired
    private AbstractCodeManager codeManager;
    @Autowired
    private AbstractTokenManager tokenManager;
    @Autowired
    private AbstractTicketGrantingTicketManager tgtManager;
    @Autowired(required = false)
    private AbstractDeviceManager deviceManager;

    /**
     * 获取accessToken
     *
     * @param clientId
     * @param clientSecret
     * @param code
     * @param request
     * @return
     */
    @RequestMapping(value = "/access-token", method = RequestMethod.GET)
    public Result<Token> getAccessToken(
            @RequestParam(value = BaseConstant.GRANT_TYPE) String grantType,
            @RequestParam(value = BaseConstant.CLIENT_ID) String clientId,
            @RequestParam(value = BaseConstant.CLIENT_SECRET) String clientSecret,
            @RequestParam(value = BaseConstant.AUTH_CODE) String code,
            @RequestParam(value = BaseConstant.LOGOUT_URI) String logoutUri,
            HttpServletRequest request) {

        // 校验授权码方式
        if (!GrantTypeEnum.AUTHORIZATION_CODE.getValue().equals(grantType)) {
            return Result.error("仅支持授权码方式");
        }

        // 校验应用
        Result<Void> appResult = appManager.validate(clientId, clientSecret);
        if (!appResult.isSuccess()) {
            return Result.error(appResult.getMessage());
        }

        // 校验授权码
        CodeContent codeContent = codeManager.get(code);
        if (codeContent == null || !codeContent.getClientId().equals(clientId)) {
            return Result.error("code有误或已过期");
        }
        codeManager.remove(code);

        // 校验凭证
        TicketGrantingTicketContent tgtContent = tgtManager.get(codeContent.getTgt());
        if (tgtContent == null) {
            return Result.error("服务端TGT已过期");
        }

        Result<TokenUser> userResult = userManager.getTokenUser(tgtContent.getUserId());
        if (!userResult.isSuccess()) {
            return Result.error(userResult.getMessage());
        }

        // 创建token
        TokenContent tc = tokenManager.create(tgtContent.getUserId(), logoutUri, codeContent);

        // 刷新服务端凭证时效
        tgtManager.refresh(tc.getTgt());

        // 记录登录设备
        if (deviceManager != null) {
            String ip = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String fingerprint = request.getHeader(DEVICE_FINGERPRINT_HEADER);
            if (fingerprint == null || fingerprint.isEmpty()) {
                fingerprint = generateFingerprint(userAgent, ip);
            }
            long now = System.currentTimeMillis();
            LoginDevice device = new LoginDevice(
                    tgtContent.getUserId(), clientId, tc.getRefreshToken(),
                    fingerprint, ip, userAgent, now, now);
            deviceManager.create(tc.getRefreshToken(), device);
        }

        // 返回token
        return Result.success(new Token(tc.getAccessToken(), tokenManager.getAccessTokenTimeout(), tc.getRefreshToken(),
                tokenManager.getRefreshTokenTimeout(), userResult.getData()));
    }

    /**
     * 刷新accessToken，并延长TGT超时时间
     *
     * @param clientId
     * @param refreshToken
     * @return
     */
    @RequestMapping(value = "/refresh-token", method = RequestMethod.GET)
    public Result<Token> getRefreshToken(
            @RequestParam(value = BaseConstant.CLIENT_ID) String clientId,
            @RequestParam(value = BaseConstant.REFRESH_TOKEN) String refreshToken) {
        Result<Long> appResult = appManager.validate(clientId);
        if (!appResult.isSuccess()) {
            return Result.error(appResult.getMessage());
        }

        TokenContent atContent = tokenManager.get(refreshToken);
        if (atContent == null) {
            return Result.error("refreshToken有误或已过期");
        }

        Result<TokenUser> userResult = userManager.getTokenUser(atContent.getUserId());
        if (!userResult.isSuccess()) {
            return Result.error(userResult.getMessage());
        }

        // 创建新token（先创建再删旧，确保新RT可用于设备记录更新）
        TokenContent tc = tokenManager.create(atContent);

        // 更新设备记录的refreshToken和最后刷新时间
        if (deviceManager != null) {
            deviceManager.updateRefreshToken(refreshToken, tc.getRefreshToken(), System.currentTimeMillis());
        }

        // 删除原有token
        tokenManager.remove(refreshToken);

        // 刷新服务端凭证时效
        tgtManager.refresh(tc.getTgt());

        // 返回新token
        return Result.success(new Token(tc.getAccessToken(), tokenManager.getAccessTokenTimeout(), tc.getRefreshToken(),
                tokenManager.getRefreshTokenTimeout(), userResult.getData()));
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 根据User-Agent和IP生成设备指纹
     */
    private String generateFingerprint(String userAgent, String ip) {
        String raw = (userAgent != null ? userAgent : "") + "|" + (ip != null ? ip : "");
        return String.valueOf(raw.hashCode());
    }
}
