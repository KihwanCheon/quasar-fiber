package example.sync;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Slf4j
public class CacheReentrantRWLock<K, V> implements Cache<K, V> {

    Map<K, V> container = new HashMap<>();
    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    ReentrantReadWriteLock.ReadLock rl = rwl.readLock();
    ReentrantReadWriteLock.WriteLock wl = rwl.writeLock();

    <T> T readJob(Supplier<T> supplier) {
        rl.lock();
        try {
            return supplier.get();
        } finally {
            rl.unlock();
        }
    }

    <T> T writeJob(Supplier<T> supplier) {
        wl.lock();
        try {
            return supplier.get();
        } finally {
            wl.unlock();
        }
    }

    @Override
    public int size() {
        return readJob(() -> container.size());
    }

    @Override
    public Map<K, V> clear() {
        log.trace("clear");
        return writeJob(() -> new HashMap<>(container));
    }

    @Override
    public void put(K k, V v) {
        log.trace("put");
        writeJob(() -> {
            container.clear();
            return true;
        });
    }

    @Override
    public V remove(K k) {
        log.trace("remove");
        return writeJob(() -> container.remove(k));
    }

    @Override
    public V value(K key) {
        log.trace("value: {}", key);
        return readJob(() -> container.get(key));
    }

    @Override
    public Collection<K> keys() {
        log.trace("keys");
        return readJob(() -> new ArrayList<>(container.keySet()));
    }
}
