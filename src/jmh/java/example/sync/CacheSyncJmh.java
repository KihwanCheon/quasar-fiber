package example.sync;

import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@State(Scope.Benchmark)
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CacheSyncJmh {

    @Benchmark
    public void synchronized_test_1() {
        Cache<Integer, String> is = new CacheSynchronized<>();
        run(is);
    }

    @Benchmark
    public void reentrantRW_test_1() {
        Cache<Integer, String> is = new CacheReentrantRWLock<>();
        run(is);
    }

    static class Task implements Runnable {
        final Cache<Integer, String> cache;
        Task(Cache<Integer, String> cache) {
            this.cache = cache;
        }

        @Override
        public void run() {
            log.info("start~ {}", Thread.currentThread());
            for (int i = 0; i < 10000; ++i) {
                int before = cache.keys().size();
                if (i % 1000 == 0) {
                    Map<Integer, String> cleared = cache.clear();
                    log.debug("before:{}, cleared:{}, after:{}", before, cleared.size(), cache.size());
                } else if (i % 10 == 0) {
                    cache.put(i, Integer.toString(i));
                    int after = cache.size();
                    log.debug("before:{}, after:{}", before, after);
                }
            }
            log.info("end~ {}", Thread.currentThread());
        }
    }

    private void run(Cache<Integer, String> is) {
        List<Thread> list = new ArrayList<>(10);
        for (int i = 0; i < 10; ++i) {
            list.add(new Thread(new Task(is)));
        }

        for (var t: list) {
            t.start();
        }

        for (var t: list) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}