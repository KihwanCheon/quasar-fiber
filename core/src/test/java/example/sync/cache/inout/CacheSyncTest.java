package example.sync.cache.inout;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;

import static example.sync.cache.Consts.*;

@Slf4j
class CacheSyncTest {

    @Test
    void synchronized_test_0() {
        Cache<Integer, String> is = new CacheSynchronized<>();
        run(is);
    }

    @Test
    void synchronized_test_1() {
        Cache<Integer, String> is = new CacheSynchronized<>();
        run(is);
    }

    @Test
    void reentrant_test_0() {
        Cache<Integer, String> is = new CacheReentrantLock<>();
        run(is);
    }

    @Test
    void reentrant_test_1() {
        Cache<Integer, String> is = new CacheReentrantLock<>();
        run(is);
    }

    @Test
    void reentrantRW_test_0() {
        Cache<Integer, String> is = new CacheReentrantRWLock<>();
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
        Random rnd = new Random();

        @Override
        public void run() {
            log.info("start~ {}", Thread.currentThread());
            for (int i = 0; i < loop_count; ++i) {
                int before = cache.keys().size();

                int r = rnd.nextInt(loop_count);
                if (r < refresh_ratio) {
                    Map<Integer, String> cleared = cache.clear();
                    log.debug("before:{}, cleared:{}, after:{}", before, cleared.size(), cache.size());
                } else {
                    if (rnd.nextBoolean()) {
                        cache.put(i, Integer.toString(i));
                    } else {
                        List<Integer> keys = cache.keys();
                        if (keys.size() > 0) {
                            String value = cache.value(keys.get(rnd.nextInt(keys.size())));
                            log.debug("before:{}, value:{}", before, value);
                        }
                    }
                }
            }
            log.info("end~ {}", Thread.currentThread());
        }
    }

    private void run(Cache<Integer, String> is) {

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