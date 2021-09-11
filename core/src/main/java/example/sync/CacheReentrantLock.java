package example.sync;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
public class CacheReentrantLock<K, V> implements Cache<K, V> {

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
    public Map<K, V> clear() {
        log.trace("clear");
        return lockJob(() -> {
            HashMap<K, V> kvHashMap = new HashMap<>(container);
            container.clear();
            return kvHashMap;
        });
    }

    @Override
    public void put(K k, V v) {
        log.trace("put");
        lockJob(() -> {
            container.put(k, v);
            return true;
        });
    }

    @Override
    public V remove(K k) {
        log.trace("remove");
        return lockJob(() -> container.remove(k));
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
