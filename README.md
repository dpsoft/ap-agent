# AP-Agent
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dpsoft/ap-agent)](https://search.maven.org/search?q=ap-agent)
[![Maven CI/CD](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml/badge.svg)](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Async Profiler Agent is a minimal Java agent that allows you to proxy to [Async Profiler] via a minimal REST API, making it easy to profile your applications. Simply add it to the start of the JVM and as it uses the [AP-Loader], there is no need for Async Profiler up front.


## Usage
Download the [latest version](https://s01.oss.sonatype.org/service/local/repositories/releases/content/io/github/dpsoft/ap-agent/0.1.9/ap-agent-0.1.9.jar).

To use the `AP-Agent`, simply add it to the JVM startup. The agent exposes a REST API for profiling with the following endpoint: `http://localhost:8080/profiler/profile`.

```shell
java -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar
```

The endpoint accepts the following parameters:

* `event`: The type of event to profile (e.g. `cpu`, `itimer`, `ctimer`, `wall` `nativemem`)
* `output`: The desired output format (e.g. `flamegraph`, `heatmap`, `jfr`, `pprof`, `collapsed`)
* `params`: Additional parameters to pass to the flame graph (e.g. `simple`, `title=My Title`, `threads`, `reverse`)
* `duration`: The length of time to profile for (in seconds)

### Flame Graph
For example, to profile CPU usage for 30 seconds and output the results in Flamegraph format, the following API call would be used: `http://localhost:8080/profiler/profile?event=cpu&output=flame&duration=30`

![image](https://user-images.githubusercontent.com/2567525/214323977-af9a4c92-8cbc-48dd-a0c6-f1f7a37122ee.png)

### Flame Graph from Collapsed Stack Traces
The collapsed stack trace format is a collection of call stacks, where each line represents a semicolon-separated list of frames followed by a counter. The frames represent the function calls in the stack and the counter indicates how many times that particular stack has been executed.

The format is as follows:
```shell
main;run;doSomething;processData;readFile;open;readBytes:5
main;run;doSomething;processData;readFile;open;readBytes:3
main;run;doSomething;processData;readFile;open;readBytes:2
main;run;doSomething;processData;readFile;close:1
main;run;doSomething;processData;writeFile;open;writeBytes:4
main;run;doSomething;processData;writeFile;close:1
```

To generate a flame graph from the collapsed stack trace format, and share it easily using [flamegraph.com], you can use the following command:

```shell
curl http://localhost:8080/profiler/profile?event=cpu&output=collapsed&duration=30 | curl --data-binary @- https://flamegraph.com | jq -r '."url"' 
...
...
https://flamegraph.com/share/4672162e-a978-11ed-aa32-fa99570776b6
```
Finally, you can open the URL in your browser to view the flame graph.

![image](https://user-images.githubusercontent.com/2567525/218182805-34568aa7-71ae-420e-9385-1b788918956b.png)

## Continuous Profiling a la Bash
We can create a simple bash script to continuously profile our application and output the results to a file. 

```bash
#!/bin/bash

event=${1:-itimer}
profiling_duration=${2:-30}
results_folder=${3:-profiling_results}

mkdir -p $results_folder

while true; do
    timestamp=$(date +%Y-%m-%d_%H-%M-%S)
    output_file="${event}_profile_$timestamp.html"
    start_time=$(date +%s)
    curl -s "http://localhost:8080/profiler/profile?event=$event&output=flame&duration=$profiling_duration" -o "$results_folder/$output_file"
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    echo "Profile saved to $results_folder/$output_file at $(date) took $duration seconds."
done
```
Running the script with the `cpu` event and `60 second` duration, we can see the results in the `profiling_results` folder.

```shell
./loop.sh cpu 60 profiling_results

Profile saved to profiling_results/cpu_profile_2023-01-24_16-16-24.html at 04:17:24 took 60 seconds.
Profile saved to profiling_results/cpu_profile_2023-01-24_16-16-24.html at 04:18:24 took 60 seconds.
```

## Go Mode
The agent also supports a `GO`(lang) mode, which exposes the `/debug/pprof/profile` endpoint. This is where we can use the go [pprof] tools.

```shell
java -Dap-agent.handler.go-mode=true -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar

go tool pprof -http :8000 http://localhost:8080/debug/pprof/profile?seconds=30  
```

![image](https://user-images.githubusercontent.com/2567525/214324772-91ac9a97-13b3-4ed1-90a8-175882e79a5b.png)    

![image](https://user-images.githubusercontent.com/2567525/214325045-0907e055-8f17-45cf-9f57-c2b52c366854.png)


### Additional Endpoints
In addition, the `AP-Agent` also supports two additional endpoints:

* `/debug/pprof/block`: Returns a profiling report of contended locks that are blocking on synchronization primitives. This endpoint can help identify where resources are being locked and where contention is occurring.
* `/debug/pprof/allocs`: Returns a profiling report of memory allocations performed by the application. This endpoint can help identify where memory is being allocated and what kind of objects are consuming the most memory.

### Example using [pprof.me] 
One way to analyze the profiling results generated by the **AP-Agent** is to use pprof.me. It is a free online tool that allows you to upload profiling data and visualize it, without having to install any additional tools.

 ```shell
curl -s http://localhost:8080/debug/pprof/allocs > allocs.pb.gz
pprofme upload -d "java allocs" allocs.pb.gz
firefox | chrome https://pprof.me/a25a2a9

```

![image](https://user-images.githubusercontent.com/2567525/235777910-3f2c25e6-e66d-4d00-b3a7-eab950562368.png)

### Can I use the ap-agent as a library?

Yes, you can use the ap-agent as library, just add the following dependency to your project:

```xml
<dependency>
    <groupId>io.github.dpsoft</groupId>
    <artifactId>ap-agent</artifactId>
    <version>0.1.9</version>
</dependency>
```

and then, you can use the API as follows(spring-boot controller example):

```java
@RestController
public class PPROFController {
    private final static Logger log = LoggerFactory.getLogger(PPROFController.class);

    private final AsyncProfiler asyncProfiler = AsyncProfilerLoader.loadOrNull();

    @GetMapping(value = {"/debug/pprof/profile", "/debug/pprof/block", "/debug/pprof/allocs"})
    @ResponseBody
    public void profile(@RequestParam Map<String,String> queryParams, HttpServletRequest request, HttpServletResponse response)  {
        final var operation = Functions.lastSegment(request.getServletPath());
        final var command = Command.from(operation, queryParams);

        ProfilerExecutor
                .with(asyncProfiler, command)
                .run()
                .onSuccess(result -> result.pipeTo(response::getOutputStream))
                .onFailure(cause -> log.error("It has not been possible to execute the profiler command.", cause))
                .andFinallyTry(response::flushBuffer);
    }
}
```


## License
This code base is available under the Apache License, version 2.

[AP-Loader]: https://github.com/jvm-profiling-tools/ap-loader
[Async Profiler]: https://github.com/jvm-profiling-tools/async-profiler
[pprof]: https://go.dev/blog/pprof
[flamegraph.com]:https://flamegraph.com/
[pprof.me]: https://pprof.me/
[experimental]:  https://s01.oss.sonatype.org/content/repositories/releases/io/github/dpsoft/ap-agent-experimental/0.1.3/ap-agent-experimental-0.1.3.jar

