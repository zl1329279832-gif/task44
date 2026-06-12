package openjoe.smart.sso.server.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 设备ID生成器
 *
 * @author Joe
 */
public class DeviceIdGenerator {

    public static String generate(String clientId, String ip, String userAgent) {
        String raw = clientId + ":" + (ip == null ? "" : ip) + ":" + (userAgent == null ? "" : userAgent);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
