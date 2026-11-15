package io.github.dpsoft.ap.examples;

import java.util.Map;

/** Test access to the servlet example and its fakes from other packages. */
public final class ServletFakesAccess {
    public static Object response() {
        return new ServletFakes.Response();
    }

    public static void get(ProfilerServlet servlet, String uri, Map<String, String> params, Object response) throws Exception {
        servlet.doGet(ServletFakes.request(uri, params), ((ServletFakes.Response) response).proxy());
    }

    public static int status(Object response) {
        return ((ServletFakes.Response) response).status;
    }

    public static String header(Object response, String name) {
        return ((ServletFakes.Response) response).headers.get(name);
    }

    public static byte[] body(Object response) {
        return ((ServletFakes.Response) response).body.toByteArray();
    }

    private ServletFakesAccess() {}
}
