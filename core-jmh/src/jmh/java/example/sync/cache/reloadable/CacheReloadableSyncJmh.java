package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static example.sync.cache.Consts.*;

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
            for (int i = 0; i < loop_count; ++i) {
                int before = cache.keys().size();

                int r = rnd.nextInt(loop_count);

                if (r < refresh_ratio) {
                    CacheReloadableSyncJmh.load(cache);
                } else {
                    int after = cache.size();
                    String value = cache.value(rnd.nextInt(cache_size));
                    log.debug("before:{}, after:{}, {}", before, after, value);
                }
            }
            log.info("end~ {}", Thread.currentThread());
        }
    }

    static void load(CacheReloadable<Integer, String> is) {
        is.load((c) -> {
            for (int j = 0; j < cache_size ; ++j)
                c.put(j, Integer.toString(j));
        });
    }

    private void run(CacheReloadable<Integer, String> is) {

        CacheReloadableSyncJmh.load(is);

        List<Thread> list = new ArrayList<>(thread_cnt);
        for (int i = 0; i < thread_cnt; ++i) {
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