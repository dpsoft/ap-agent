package io.github.dpsoft.ap.examples;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Just enough of the Servlet API to run the servlet example without a container. */
final class ServletFakes {
    static HttpServletRequest request(String uri, Map<String, String> params) {
        final var parameterMap = new HashMap<String, String[]>();
        params.forEach((k, v) -> parameterMap.put(k, new String[]{v}));
        return (HttpServletRequest) Proxy.newProxyInstance(ServletFakes.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRequestURI": return uri;
                        case "getParameterMap": return parameterMap;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    static final class Response {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final Map<String, String> headers = new HashMap<>();
        int status = 200;
        String message;

        HttpServletResponse proxy() {
            final var out = new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) { }
                @Override public void write(int b) { body.write(b); }
            };
            return (HttpServletResponse) Proxy.newProxyInstance(ServletFakes.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setContentType": headers.put("Content-Type", (String) args[0]); return null;
                            case "setHeader": headers.put((String) args[0], (String) args[1]); return null;
                            case "setContentLengthLong": headers.put("Content-Length", String.valueOf(args[0])); return null;
                            case "getOutputStream": return out;
                            case "sendError": status = (int) args[0]; message = args.length > 1 ? (String) args[1] : null; return null;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    private ServletFakes() {}
}
