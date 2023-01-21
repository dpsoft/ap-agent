package dpsoft.ap.route;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dpsoft.ap.ProfilerExecutor;
import dpsoft.ap.command.Command;
import dpsoft.ap.config.AgentConfiguration;
import dpsoft.ap.functions.Functions;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;

public class AsyncProfilerHandler implements HttpHandler {
    private final AsyncProfiler asyncProfiler;
    private final AgentConfiguration configuration;

    public AsyncProfilerHandler(AsyncProfiler profiler, AgentConfiguration configuration) {
        this.asyncProfiler = profiler;
        this.configuration = configuration;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        if (!path.equals("/profiler/profile")) {
            exchange.sendResponseHeaders(404, 0L);
            exchange.close();
        } else {

            exchange.getRequestBody().close();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);

            var queryParamsMap = Functions.splitQueryParams(exchange.getRequestURI());

            ProfilerExecutor
                    .with(asyncProfiler)
                    .run(Command.from(queryParamsMap))
                    .onSuccess(p -> p.pipeTo(exchange.getResponseBody()))
                    .onFailure(cause -> Logger.error(cause, "It has not been possible to execute the profiler command."));
        }
    }
}

