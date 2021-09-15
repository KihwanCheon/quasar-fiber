package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class CacheReloadableReentrantRWLock<K, V> implements CacheReloadable<K, V> {

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
    public void load(Consumer<Map<K, V>> gen) {
        log.trace("load");
        writeJob(() -> {
            container.clear();
            gen.accept(container);
            return true;
        });
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
