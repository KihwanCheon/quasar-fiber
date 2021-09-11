package example.sync;

import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
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
            for (int i = 0; i < 1000; ++i) {
                int before = cache.size();
                if (before % 100 == 0) {
                    Map<Integer, String> cleared = cache.clear();
                    log.info("before:{}, cleared:{}", before, cleared);
                } else if (i % 10 == 0) {
                    cache.put(i, Integer.toString(i));
                    int after = cache.size();
                    log.info("before:{}, after:{}", before, after);
                }
            }
        }
    }

    private void run(Cache<Integer, String> is) {
//        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
//        executor.execute(new Task(is));
//        executor.execute(new Task(is));
//        executor.execute(new Task(is));
//        executor.execute(new Task(is));
//        executor.execute(new Task(is));
//        executor.execute(new Task(is));
//        executor.shutdown();

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
