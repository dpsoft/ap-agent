package io.github.dpsoft.ap.examples;

// tag::spans[]
import one.profiler.Span;

public class OrderService {
    public void placeOrder(String customer) {
        long start = Span.start();
        try {
            // ... the work you want to find in the profile
        } finally {
            Span.end(start, "order:customer=" + customer);
        }
    }
}
// end::spans[]
