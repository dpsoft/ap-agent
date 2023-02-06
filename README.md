# AP-Agent
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dpsoft/ap-agent)](https://search.maven.org/search?q=ap-agent)
[![Maven CI/CD](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml/badge.svg)](https://github.com/dpsoft/ap-agent/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Async Profiler Agent is a minimal Java agent that allows you to proxy to [Async Profiler] via a minimal REST API, making it easy to profile your applications. Simply add it to the start of the JVM and as it uses the [AP-Loader], there is no need for Async Profiler up front.


## Usage
To use the `AP-Agent`, simply add it to the JVM startup. The agent exposes a REST API for profiling with the following endpoint: `http://localhost:8080/profiler/profile`.

```shell
java -javaagent:/path/to/ap-agent.jar -jar /path/to/my-awesome-app.jar
```

The endpoint accepts the following parameters:

* `event`: The type of event to profile (e.g. `cpu`, `itimer`, `wall`)
* `output`: The desired output format (e.g. `flamegraph`, `hotcold`, `jfr`, `pprof`)
* `duration`: The length of time to profile for (in seconds)

For example, to profile CPU usage for 30 seconds and output the results in Flamegraph format, the following API call would be used: `http://localhost:8080/profiler/profile?event=cpu&output=flame&duration=30`

![image](https://user-images.githubusercontent.com/2567525/214323977-af9a4c92-8cbc-48dd-a0c6-f1f7a37122ee.png)


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

## TODO
- [ ] Release to Maven Central
- [ ] Add support for Context ID


## License
This code base is available ander the Apache License, version 2.

[AP-Loader]: https://github.com/jvm-profiling-tools/ap-loader
[Async Profiler]: https://github.com/jvm-profiling-tools/async-profiler
[pprof]: https://go.dev/blog/pprof
