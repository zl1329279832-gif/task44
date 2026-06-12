package openjoe.smart.sso.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import openjoe.smart.sso.base.constant.BaseConstant;
import openjoe.smart.sso.base.entity.Result;
import openjoe.smart.sso.base.entity.Token;
import openjoe.smart.sso.base.entity.TokenUser;
import openjoe.smart.sso.base.enums.GrantTypeEnum;
import openjoe.smart.sso.server.entity.CodeContent;
import openjoe.smart.sso.server.entity.LoginDeviceContent;
import openjoe.smart.sso.server.entity.TicketGrantingTicketContent;
import openjoe.smart.sso.server.entity.TokenContent;
import openjoe.smart.sso.server.manager.*;
import openjoe.smart.sso.server.util.DeviceIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
    @Autowired
    private AbstractLoginDeviceManager deviceManager;

    /**
     * 获取accessToken
     *
     * @param clientId
     * @param clientSecret
     * @param code
     * @return
     */
    @RequestMapping(value = "/access-token", method = RequestMethod.GET)
    public Result<Token> getAccessToken(
            @RequestParam(value = BaseConstant.GRANT_TYPE) String grantType,
            @RequestParam(value = BaseConstant.CLIENT_ID) String clientId,
            @RequestParam(value = BaseConstant.CLIENT_SECRET) String clientSecret,
            @RequestParam(value = BaseConstant.AUTH_CODE) String code,
            @RequestParam(value = BaseConstant.LOGOUT_URI) String logoutUri,
            @RequestParam(value = "deviceId", required = false) String deviceId,
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

        // 生成设备指纹
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasLength(deviceId)) {
            deviceId = DeviceIdGenerator.generate(clientId, ip, userAgent);
        }

        // 创建token
        TokenContent tc = tokenManager.create(tgtContent.getUserId(), logoutUri, codeContent, deviceId);

        // 记录登录设备
        long now = System.currentTimeMillis();
        LoginDeviceContent device = new LoginDeviceContent(deviceId, clientId, tgtContent.getUserId(),
                ip, userAgent, now, now, tc.getRefreshToken(), tc.getAccessToken(), codeContent.getTgt());
        deviceManager.create(deviceId, device);

        // 刷新服务端凭证时效
        tgtManager.refresh(tc.getTgt());

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

        // 删除原有token
        tokenManager.remove(refreshToken);

        // 创建新token
        TokenContent tc = tokenManager.create(atContent);

        // 更新设备活跃时间
        if (StringUtils.hasLength(atContent.getDeviceId())) {
            deviceManager.updateOnRefresh(atContent.getDeviceId(), tc.getRefreshToken(), tc.getAccessToken());
        }

        // 刷新服务端凭证时效
        tgtManager.refresh(tc.getTgt());

        // 返回新token
        return Result.success(new Token(tc.getAccessToken(), tokenManager.getAccessTokenTimeout(), tc.getRefreshToken(),
                tokenManager.getRefreshTokenTimeout(), userResult.getData()));
    }
}