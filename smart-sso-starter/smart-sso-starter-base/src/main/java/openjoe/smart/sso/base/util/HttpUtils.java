package openjoe.smart.sso.base.util;

import org.apache.http.HttpEntity;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Http请求工具
 *
 * @author Joe
 */
public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    // 连接池单例，整个应用共享
    private static final CloseableHttpClient httpClient;

    static {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200);          // 连接池最大连接数
        connManager.setDefaultMaxPerRoute(50); // 每个路由最大连接数

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)           // 建立连接超时
                .setSocketTimeout(5000)            // 读取数据超时
                .setConnectionRequestTimeout(3000) // 从连接池获取连接超时
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }


    // GET
    public static String get(String url, Map<String, String> paramMap) {
        String result = null;
        String realUrl = url;
        CloseableHttpResponse response = null;
        try {
            if (paramMap != null && !paramMap.isEmpty()) {
                List<NameValuePair> params = new ArrayList<>();
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }
                String paramStr = EntityUtils.toString(new UrlEncodedFormEntity(params, "UTF-8"));
                realUrl += "?" + paramStr;
            }
            HttpGet httpGet = new HttpGet(realUrl);
            response = httpClient.execute(httpGet);
            if (response != null && response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity, "UTF-8");
                    EntityUtils.consume(entity);
                }
                logger.debug("http get url: {}, paramMap: {}, result: {}", url, JsonUtils.toString(paramMap), result);
            }
            return result;
        } catch (Exception e) {
            logger.error("http get url: {}, paramMap: {}, result: {}", url, JsonUtils.toString(paramMap), result, e);
        } finally {
            // 只关闭 response，不关闭 httpClient（连接池共享）
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error("", e);
                }
            }
        }
        return null;
    }

    public static String get(String url) {
        return get(url, null);
    }


    // POST
    public static String post(String url, Map<String, String> paramMap, Map<String, String> headerMap) {
        HttpPost httpPost = null;
        CloseableHttpResponse response = null;
        try {
            httpPost = new HttpPost(url);
            if (paramMap != null && !paramMap.isEmpty()) {
                List<NameValuePair> formParams = new ArrayList<>();
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }
                httpPost.setEntity(new UrlEncodedFormEntity(formParams, "UTF-8"));
            }
            if (headerMap != null && !headerMap.isEmpty()) {
                for (Map.Entry<String, String> headerItem : headerMap.entrySet()) {
                    httpPost.setHeader(headerItem.getKey(), headerItem.getValue());
                }
            }
            response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            if (entity != null && response.getStatusLine().getStatusCode() == 200) {
                String result = EntityUtils.toString(entity, "UTF-8");
                EntityUtils.consume(entity); // 确保连接归还连接池
                logger.debug("http post url: {}, result: {}", url, result);
                return result;
            }
            return null;
        } catch (Exception e) {
            logger.error("http post url: {}, paramMap: {}", url, paramMap, e);
            return null;
        } finally {
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            // 只关闭 response，不关闭 httpClient（连接池共享）
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error("", e);
                }
            }
        }
    }

    public static String post(String url, Map<String, String> paramMap) {
        return post(url, paramMap, null);
    }

    public static String postHeader(String url, Map<String, String> headerMap) {
        return post(url, null, headerMap);
    }
}
