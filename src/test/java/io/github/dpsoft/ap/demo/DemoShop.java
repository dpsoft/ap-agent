package io.github.dpsoft.ap.demo;

import one.profiler.Span;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;

/**
 * A small, realistic-looking workload for the documentation images: request threads place orders that parse JSON,
 * price items, reserve stock under a lock, wait on a "database" and compress a receipt (native memory). Every order
 * is a span tagged {@code order:customer=<id>}; customer 3, a bulk importer, places few orders but each one computes
 * expensive volume discounts: a thin slice of the whole profile that dominates once filtered by its tag.
 */
public final class DemoShop {
    public static void main(String[] args) throws Exception {
        final var threads = new ArrayList<Thread>();
        for (int i = 1; i <= 4; i++) {
            final var thread = new Thread(DemoShop::serve, "http-nio-exec-" + i);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
        for (var thread : threads) thread.join();
    }

    private static void serve() {
        final var orders = new OrderService();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // customer 3, the bulk importer, places one order in twenty
                final int roll = ThreadLocalRandom.current().nextInt(20);
                orders.placeOrder(roll == 0 ? BULK_IMPORTER : 1 + roll % 2);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static final int BULK_IMPORTER = 3;

    static final class OrderService {
        private final JsonCodec json = new JsonCodec();
        private final PricingEngine pricing = new PricingEngine();
        private final ReceiptCompressor receipts = new ReceiptCompressor();

        void placeOrder(int customer) throws InterruptedException {
            final long start = Span.start();
            try {
                final var order = json.parse(json.render(customer, 240));
                var total = pricing.price(order);
                if (customer == BULK_IMPORTER) total = pricing.volumeDiscount(order, total);
                Inventory.reserve(order);
                OrderRepository.save(order, total);
                receipts.compress(order, total);
            } finally {
                Span.end(start, "order:customer=" + customer);
            }
        }
    }

    static final class JsonCodec {
        String render(int customer, int items) {
            final var json = new StringBuilder("{\"customer\":").append(customer).append(",\"items\":[");
            for (int i = 0; i < items; i++) {
                if (i > 0) json.append(',');
                json.append("{\"sku\":\"SKU-").append(i).append("\",\"qty\":").append(1 + i % 5)
                    .append(",\"price\":\"").append(9 + i % 90).append(".99\"}");
            }
            return json.append("]}").toString();
        }

        List<Map<String, String>> parse(String json) {
            final var items = new ArrayList<Map<String, String>>();
            for (var raw : json.substring(json.indexOf('[') + 1, json.lastIndexOf(']')).split("\\},\\{")) {
                final var item = new HashMap<String, String>();
                for (var field : raw.replace("{", "").replace("}", "").split(",")) {
                    final var kv = field.split(":");
                    item.put(kv[0].replace("\"", ""), kv[1].replace("\"", ""));
                }
                items.add(item);
            }
            return items;
        }
    }

    static final class PricingEngine {
        BigDecimal price(List<Map<String, String>> items) {
            var total = BigDecimal.ZERO;
            for (var item : items) {
                final var line = new BigDecimal(item.get("price")).multiply(new BigDecimal(item.get("qty")));
                final var tax = line.multiply(new BigDecimal("0.21")).setScale(2, RoundingMode.HALF_EVEN);
                total = total.add(line).add(tax);
            }
            return total;
        }

        /** Tiered discounts recomputed per item: deliberately the hot spot of the bulk importer's orders. */
        BigDecimal volumeDiscount(List<Map<String, String>> items, BigDecimal total) {
            var discount = BigDecimal.ZERO;
            for (int i = 0; i < items.size() * 12; i++) {
                final var tier = BigDecimal.ONE.add(new BigDecimal(i).divide(new BigDecimal(items.size()), 12, RoundingMode.HALF_EVEN));
                discount = discount.add(total.multiply(tier.pow(3)).divide(new BigDecimal(1_000_000), 6, RoundingMode.HALF_EVEN));
            }
            return total.subtract(discount).max(BigDecimal.ZERO);
        }
    }

    static final class Inventory {
        private static final Object STOCK = new Object();

        static void reserve(List<Map<String, String>> items) throws InterruptedException {
            synchronized (STOCK) {
                Thread.sleep(1); // holding the stock lock: other request threads queue up here
            }
        }
    }

    static final class OrderRepository {
        static void save(List<Map<String, String>> items, BigDecimal total) throws InterruptedException {
            Thread.sleep(4); // waiting on the database
        }
    }

    static final class ReceiptCompressor {
        void compress(List<Map<String, String>> items, BigDecimal total) {
            final var deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try {
                deflater.setInput((items.toString() + total).getBytes());
                deflater.finish();
                final var out = new byte[64 * 1024];
                while (!deflater.finished()) deflater.deflate(out);
            } finally {
                deflater.end();
            }
        }
    }

    private DemoShop() {}
}
