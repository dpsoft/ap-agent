# AP-Agent

Async Profiler Agent is a minimal Java agent that allows you to proxy to [Async Profiler] via a minimal REST API, making it easy to profile your applications. Simply add it to the start of the JVM and as it uses the [AP-Loader], there is no need for Async Profiler up front.


## Usage
To use the `AP-Agent`, simply add it to the JVM startup. The agent exposes a REST API for profiling with the following endpoint: `http://localhost:8080/profiler/profile`.

```bash
java -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar
```

The endpoint accepts the following parameters:

* `event`: The type of event to profile (e.g. `cpu`, `itime`, `wall`)
* `output`: The desired output format (e.g. `flamegraph`, `jfr`, `pprof`)
* `duration`: The length of time to profile for (in seconds)

For example, to profile CPU usage for 30 seconds and output the results in Flamegraph format, the following API call would be used: `http://localhost:8080/profiler/profile?event=cpu&output=flame&duration=30`

![image](https://user-images.githubusercontent.com/2567525/214323977-af9a4c92-8cbc-48dd-a0c6-f1f7a37122ee.png)


## Go Mode
The agent also supports a `GO`(lang) mode, which exposes the `/debug/pprof/profile` endpoint. This is where we can use the go [pprof] tools.

```bash
java -Dap-agent.handler.go-mode=true -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar

go tool pprof -http :8000 http://localhost:8080/debug/pprof/profile?seconds=30  
```

![image](https://user-images.githubusercontent.com/2567525/214324772-91ac9a97-13b3-4ed1-90a8-175882e79a5b.png)    

![image](https://user-images.githubusercontent.com/2567525/214325045-0907e055-8f17-45cf-9f57-c2b52c366854.png)

## Contribution
We welcome contributions to this project. If you would like to contribute, please fork the repository and submit a pull request.


[AP-Loader]: https://github.com/jvm-profiling-tools/ap-loader
[Async Profiler]: https://github.com/jvm-profiling-tools/async-profiler
[pprof]: https://go.dev/blog/pprof
