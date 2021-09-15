package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CacheReloadableSyncJmh {

    @Benchmark
    public void synchronized_test_1() {
        CacheReloadable<Integer, String> is = new CacheReloadableSynchronized<>();
        run(is);
    }

    @Benchmark
    public void reentrant_test_1() {
        CacheReloadable<Integer, String> is = new CacheReloadableReentrantLock<>();
        run(is);
    }

    @Benchmark
    public void reentrantRW_test_1() {
        CacheReloadable<Integer, String> is = new CacheReloadableReentrantRWLock<>();
        run(is);
    }

    static class Task implements Runnable {
        final CacheReloadable<Integer, String> cache;
        Task(CacheReloadable<Integer, String> cache) {
            this.cache = cache;
        }
        Random rnd = new Random();

        @Override
        public void run() {
            log.info("start~ {}", Thread.currentThread());
            for (int i = 0; i < 10000; ++i) {
                int before = cache.keys().size();
                if (i % 1000 == 0) {
                    cache.load((c) -> {
                        for (int j = 0; j < 10000 ; ++j)
                            c.put(j, Integer.toString(j));
                    });
                } else if (i % 10 == 0) {
                    int after = cache.size();
                    String value = cache.value(rnd.nextInt(10000));
                    log.debug("before:{}, after:{}, {}", before, after, value);
                }
            }
            log.info("end~ {}", Thread.currentThread());
        }
    }

    private void run(CacheReloadable<Integer, String> is) {
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