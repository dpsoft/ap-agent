# Spring Boot Demo - Manual PProf Integration

Run with:

```bash
./gradlew bootRun
```

Get a CPU profile with:

```bash
go tool pprof -http :6060 "http://localhost:8080/debug/pprof/profile?seconds=10"
```

Get an Allocation profile with:

```bash
go tool pprof -http :6060 "http://localhost:8080/debug/pprof/allocs"
```

Get a Block profile with:

```bash
go tool pprof -http :6060 "http://localhost:8080/debug/pprof/block"
```
