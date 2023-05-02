package io.github.dpsoft.ap;

import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.util.Banner;
import io.github.dpsoft.ap.util.Runner;
import io.github.dpsoft.ap.util.Server;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation inst)  {
        Runner.runWith((profiler, configuration) -> {

            Banner.show(configuration);

            Server.with(configuration, (server) -> {
                final var profilerHandler = new AsyncProfilerHandler(profiler, configuration);
                server.createContext("/", profilerHandler);
            });
        });
    }

    public static void main(String[] args) {
        premain("", null);
        while (true) {
            allocArray();
            primes();
        }

    }

    public static void allocArray() {
        int[] testarray = new int[1000];

        for (int i = 0; i < testarray.length; i++) {
            testarray[i] = i;
        }
    }

    public static void primes() {
        while (true) {
            int n = 1000000;
            int[] primes = new int[n];
            int count = 0;
            for (int i = 2; i < n; i++) {
                boolean isPrime = true;
                for (int j = 0; j < count; j++) {
                    if (i % primes[j] == 0) {
                        isPrime = false;
                        break;
                    }
                }
                if (isPrime) {
                    primes[count++] = i;
                }
            }
        }
    }
}
