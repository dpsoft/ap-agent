# AP-Agent
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dpsoft/ap-agent)](https://search.maven.org/search?q=ap-agent)
[![Maven CI/CD](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml/badge.svg)](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Async Profiler Agent is a minimal Java agent that allows you to proxy to [Async Profiler] via a minimal REST API, making it easy to profile your applications. Simply add it to the start of the JVM and as it uses the [AP-Loader], there is no need for Async Profiler up front.


## Usage
Download the [latest version](https://s01.oss.sonatype.org/service/local/repositories/releases/content/io/github/dpsoft/ap-agent/0.1.1/ap-agent-0.1.1.jar).

To use the `AP-Agent`, simply add it to the JVM startup. The agent exposes a REST API for profiling with the following endpoint: `http://localhost:8080/profiler/profile`.

```shell
java -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar
```

The endpoint accepts the following parameters:

* `event`: The type of event to profile (e.g. `cpu`, `itimer`, `wall`)
* `output`: The desired output format (e.g. `flamegraph`, `hotcold`, `jfr`, `pprof`, `collapsed`, `fp`)
* `params`: Additional parameters to pass to the flame graph (e.g. `simple`, `title=My Title`, `threads`, `reverse`)
* `duration`: The length of time to profile for (in seconds)

### Flame Graph
For example, to profile CPU usage for 30 seconds and output the results in Flamegraph format, the following API call would be used: `http://localhost:8080/profiler/profile?event=cpu&output=flame&duration=30`

![image](https://user-images.githubusercontent.com/2567525/214323977-af9a4c92-8cbc-48dd-a0c6-f1f7a37122ee.png)

### Hot/Cold Flame Graph
This type of visualization combines both `on-CPU` and `off-CPU` flame graphs. This visualization provides a comprehensive view of the performance data by showing all thread time in one graph and allowing direct comparisons between `on-CPU` and `off-CPU` code path durations.

For example, the following API call would be used: `http://localhost:8080/profiler/profile?event=cpu&output=hotcold&duration=30`

![image](https://user-images.githubusercontent.com/2567525/217419824-5d982e67-8175-4239-9b42-c7dbe58dd452.png)

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

<imagen>

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

## Firefox Profiler

1. [Examples](#examples)
2. [Profiling results](#profiling-results)

### Examples

1. [Basic example with `curl`](#basic-example-with-curl)
2. [Example using `jfrtofp-server`](#example-using-jfrtofp-server)
3. [Example using the `loop.sh` script](#example-using-the-loopsh-script)

#### Basic example with `curl`

1. Execute the profiler for the `cpu` event, `fp` (Firefox Profiler) output, a `60 seconds` duration and write the response to `profiling_results/firefox-profiler-example.json.gz`

```shell
curl -s "http://localhost:8080/profiler/profile?event=cpu&output=fp&duration=60" -o profiling_results/firefox-profiler-example.json.gz
```

2. Visit the [Firefox Profiler page](https://profiler.firefox.com)

![Screenshot 2023-02-04 at 13 12 49](https://user-images.githubusercontent.com/18125567/216790473-2749c404-5b1b-41c8-bb3c-fc3854e60f1b.png)

3. Load the output file from `step 1`, and you'll see the profiling result

#### Example using `jfrtofp-server`

1. Execute the profiler for the `cpu` event, `fp` (Firefox Profiler) output, a `60 seconds` duration and write the response to `profiling_results/firefox-profiler-example.json.gz`

```shell
curl -s "http://localhost:8080/profiler/profile?event=cpu&output=fp&duration=60" -o profiling_results/firefox-profiler-example.json.gz
```

2. Start the [jfrtofp-server](https://github.com/parttimenerd/jfrtofp-server), you can follow the steps from the [README](https://github.com/parttimenerd/jfrtofp-server#jfrtofp-server), with the output file from `step 1` as an argument
```shell
java -jar jfrtofp-server-all.jar profiling_results/firefox-profiler-example.json.gz
```

3. The [jfrtofp-server](https://github.com/parttimenerd/jfrtofp-server) will log a message like `Navigate to http://localhost:55287/from-url/http%3A%2F%2Flocalhost%3A55287%2Ffiles%firefox-profiler-example.json.gz to launch the profiler view`

4. Just click that link, and you will see the profiling result in the `Firefox Profiler` page

#### Example using the `loop.sh` script

1. Continuously profile the application for the `cpu` event, `fp` (Firefox Profiler) output, a `60 seconds` duration and write the execution results to `profiling_results/` folder

```shell
./loop.sh cpu 60 profiling_results fp

Profile saved to profiling_results/cpu_profile_2023-01-24_16-16-24.json.gz at 04:17:24 took 60 seconds.
Profile saved to profiling_results/cpu_profile_2023-01-24_16-16-24.json.gz at 04:18:24 took 60 seconds.
```

2. Visit the [Firefox Profiler page](https://profiler.firefox.com)
3. Load the output file from `step 1`, and you'll see the profiling result

### Profiling results

#### Call tree

![image](https://user-images.githubusercontent.com/2567525/216951998-615f4164-acae-4635-b5b6-c2c3e053a07c.png)

#### Flame graph

![image](https://user-images.githubusercontent.com/2567525/216951636-70818765-c8aa-44f6-a452-f998a3e1f735.png)

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
java -Dap-agent.handler.go-mode=true -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar

curl -s http://localhost:8080/debug/pprof/block > block.pb.gz
pprofme upload -d "java locks" block.pb.gz
firefox | chrome https://pprof.me/3de5295
```

![image](https://user-images.githubusercontent.com/2567525/235777767-0c6bf7c0-c19b-4613-8a5d-bb7136166b15.png)

 ```shell
curl -s http://localhost:8080/debug/pprof/allocs > allocs.pb.gz
pprofme upload -d "java allocs" allocs.pb.gz
firefox | chrome https://pprof.me/a25a2a9

```

![image](https://user-images.githubusercontent.com/2567525/235777910-3f2c25e6-e66d-4d00-b3a7-eab950562368.png)


## TODO
- [ ] Add support for Context ID


## License
This code base is available ander the Apache License, version 2.

[AP-Loader]: https://github.com/jvm-profiling-tools/ap-loader
[Async Profiler]: https://github.com/jvm-profiling-tools/async-profiler
[pprof]: https://go.dev/blog/pprof
[flamegraph.com]:https://flamegraph.com/
[pprof.me]: https://pprof.me/
