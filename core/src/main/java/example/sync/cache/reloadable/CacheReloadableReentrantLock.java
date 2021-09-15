package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class CacheReloadableReentrantLock<K, V> implements CacheReloadable<K, V> {

    Map<K, V> container = new HashMap<>();
    ReentrantLock lock = new ReentrantLock();



    <T> T lockJob(Supplier<T> supplier) {
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        return lockJob(() -> container.size());
    }

    @Override
    public void load(Consumer<Map<K, V>> gen) {
        log.trace("load");
        lockJob(() -> {
            container.clear();
            gen.accept(container);
            return true;
        });
    }

    @Override
    public V value(K key) {
        log.trace("value: {}", key);
        return lockJob(() -> container.get(key));
    }

    @Override
    public Collection<K> keys() {
        log.trace("keys");
        return lockJob(() -> new ArrayList<>(container.keySet()));
    }
}
