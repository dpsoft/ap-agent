# Upgrading from 0.1.x

* **Fixed outputs**: `output=collapsed` now returns collapsed stacks (it returned the JFR recording),
  `event=wall` flame graphs have samples (they came out empty), and `output=pprof` honours `event`
  (it always recorded CPU unless the Go path said otherwise).
* **Errors**: invalid parameters return `400` with a message instead of closing the connection, failures
  return `500`, and every response has a `Content-Type`; JFR and pprof declare `Content-Encoding: gzip`.
* **Library API**: use `ProfilerService` and `Profile`. `Command` and `ProfilerExecutor` are now internal and
  no longer use Vavr types (`Try`, `Option`, Vavr `List`).
* **Smaller jar**: Vavr was removed, the shaded jar went from 2.8 MB to 1.8 MB.

Every request in [`CompatibilityIT`](../src/test/java/io/github/dpsoft/ap/it/CompatibilityIT.java) is compared
against the last published release; the differences above are the only ones it allows.
