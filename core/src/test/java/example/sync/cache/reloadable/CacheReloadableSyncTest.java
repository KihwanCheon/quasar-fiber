package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static example.sync.cache.Consts.*;

@Slf4j
class CacheReloadableSyncTest {
    @Test
    void synchronized_test_0() {
        CacheReloadable<Integer, String> is = new CacheReloadableSynchronized<>();
        run(is);
    }

    @Test
    void synchronized_test_1() {
        CacheReloadable<Integer, String> is = new CacheReloadableSynchronized<>();
        run(is);
    }

    @Test
    void reentrant_test_0() {
        CacheReloadable<Integer, String> is = new CacheReloadableReentrantLock<>();
        run(is);
    }

    @Test
    void reentrant_test_1() {
        CacheReloadable<Integer, String> is = new CacheReloadableReentrantLock<>();
        run(is);
    }

    @Test
    void reentrantRW_test_0() {
        CacheReloadable<Integer, String> is = new CacheReloadableReentrantRWLock<>();
        run(is);
    }

    @Test
    void reentrantRW_test_1() {
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
                    CacheReloadableSyncTest.load(cache);
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

        CacheReloadableSyncTest.load(is);

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