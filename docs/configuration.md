# Configuration

Every setting can be overridden with a JVM system property, e.g. `-Dap-agent.server.port=9090`.

| Property | Default | Description |
|---|---|---|
| `ap-agent.server.host` | `0.0.0.0` | Address the HTTP server binds to. |
| `ap-agent.server.port` | `8080` | Port of the HTTP server. |
| `ap-agent.show-banner` | `true` | Print the banner at startup. |
| `ap-agent.profiler.interval` | `10000000` | Default sampling interval (also `PROFILER_SAMPLE_INTERVAL`). |
| `ap-agent.handler.context` | `/profiler/profile` | Path of the main endpoint. |
| `ap-agent.handler.go-mode` | `false` | Serve the `/debug/pprof/*` endpoints and default to pprof. |

> **Security**: the endpoint has no authentication and listens on all interfaces by default. Bind it to
> `127.0.0.1` (`-Dap-agent.server.host=127.0.0.1`) or keep the port private to your network.

## Docker and Kubernetes

```dockerfile
ADD https://repo1.maven.org/maven2/io/github/dpsoft/ap-agent/0.2.0/ap-agent-0.2.0.jar /opt/ap-agent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/ap-agent.jar"
EXPOSE 8080
```

```shell
kubectl port-forward deploy/my-app 8080:8080
curl -o cpu.html "http://localhost:8080/profiler/profile?output=flame&duration=30"
```

The `cpu` event uses Linux perf events, which containers usually restrict (`perf_event_paranoid`, seccomp).
The default `ctimer` event and `itimer` work without extra privileges.

## Requirements

* Java 11 or newer (tested on 11, 17, 21 and 25).
* Linux x64 or arm64 (glibc), or macOS 11+. Other platforms log an error and the application keeps running.

[async-profiler]: https://github.com/async-profiler/async-profiler
[AP-Loader]: https://github.com/jvm-profiling-tools/ap-loader
[converter options]: https://github.com/async-profiler/async-profiler/blob/master/docs/ConverterUsage.md
[pprof]: https://github.com/google/pprof
