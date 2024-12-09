

import com.sun.net.httpserver.Headers;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Request implements com.sun.net.httpserver.Request {

    private URI requestUri;
    private String requestMethod;
    private List<NameValuePair> params;
    private List<NameValuePair> bodyParams;

    public Request(String method, String patch) {
        params = new ArrayList<>();
        requestMethod = method;
        int indexEndPatch = patch.indexOf('?');
        if (indexEndPatch != -1) {
            String uri = patch.substring(0, indexEndPatch);
            requestUri = URI.create(uri);
            String param = patch.substring(indexEndPatch + 1, patch.length());
            params.addAll(URLEncodedUtils.parse(param, StandardCharsets.UTF_8));
        } else {
            requestUri = URI.create(patch);
        }
    }

    @Override
    public URI getRequestURI() {

        return requestUri;
    }

    @Override
    public String getRequestMethod() {
        return requestMethod;
    }

    @Override
    public Headers getRequestHeaders() {
        return null;
    }

    public List<NameValuePair> getQueryParam(String name) {
        List<NameValuePair> getListParam = new ArrayList<>();
        for (NameValuePair param : params) {
            if (param.getName().equals(name)) {
                getListParam.add(param);
            }
        }
        return getListParam;
    }

    public List<NameValuePair> getQueryParams() {
        return params;
    }

    public void addBody(String contentType, String body) {
        if (contentType.equals("text/plain")) {
            List<String> listParams = List.of(body.split("\r\n"));

            for (String param : listParams) {
                String[] nameValue = param.split("=");
                NameValuePair nameValuePair = new NameValuePair() {

                    @Override
                    public String getName() {
                        return nameValue[0];
                    }

                    @Override
                    public String getValue() {
                        return nameValue[1];
                    }
                };
                params.add(nameValuePair);
            }

        } else {
            params.addAll(URLEncodedUtils.parse(body, StandardCharsets.UTF_8));
        }

    }
}
