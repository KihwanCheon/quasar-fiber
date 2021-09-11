package example.sync;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CacheSyncTest {

    @Test
    void synchronized_test_1() {
        Cache<Integer, String> is = new CacheSynchronized<>();
        run(is);
    }

    @Test
    void reentrantRW_test_1() {
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
            for (int i = 0; i < 10000; ++i) {
                int before = cache.size();
                cache.put(i, Integer.toString(i));
                if (i % 1000 == 0) {
                    Map<Integer, String> cleared = cache.clear();
                    log.info("before:{}, cleared:{}", before, cleared.size());
                } else if (i % 100 == 0) {
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