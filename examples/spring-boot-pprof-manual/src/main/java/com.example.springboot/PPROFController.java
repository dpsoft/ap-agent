package com.example.springboot;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.functions.Functions;
import io.github.dpsoft.ap.libs.one.profiler.AsyncProfiler;
import io.github.dpsoft.ap.libs.one.profiler.AsyncProfilerLoader;
import io.github.dpsoft.ap.util.ProfilerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
public class PPROFController {
    private final static Logger log = LoggerFactory.getLogger(PPROFController.class);

    private final AsyncProfiler asyncProfiler = AsyncProfilerLoader.loadOrNull();

    @GetMapping(value = {"/debug/pprof/profile", "/debug/pprof/block", "/debug/pprof/allocs"})
    @ResponseBody
    public void profile(@RequestParam Map<String,String> queryParams, HttpServletRequest request, HttpServletResponse response)  {
        final var operation = Functions.lastSegment(request.getServletPath());
        final var command = Command.from(operation, queryParams);

        try {
            ProfilerExecutor
                    .with(asyncProfiler, command)
                    .run()
                    .pipeTo(response::getOutputStream);
        } catch (Exception cause) {
            log.error("It has not been possible to execute the profiler command.", cause);
        } finally {
            try {
                response.flushBuffer();
            } catch (Exception e) {
                log.error("Error flushing response buffer", e);
            }
        }
    }
}