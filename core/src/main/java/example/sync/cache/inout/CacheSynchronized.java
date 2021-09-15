package example.sync.cache.inout;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Supplier;

@Slf4j
public class CacheSynchronized<K, V> implements Cache<K, V> {

    Map<K, V> container = new HashMap<>();

    synchronized <T> T sync(Supplier<T> supplier) {
        return supplier.get();
    }


    @Override
    public int size() {
        return sync(() -> container.size());
    }

    @Override
    public Map<K, V> clear() {
        log.trace("clear");
        return sync(() -> {
            HashMap<K, V> kvHashMap = new HashMap<>(container);
            container.clear();
            return kvHashMap;
        });
    }

    @Override
    public void put(K k, V v) {
        log.trace("put");
        sync(() -> {
            container.put(k, v);
            return true;
        });
    }

    @Override
    public V remove(K k) {
        log.trace("remove");
        return sync(() -> container.remove(k));
    }

    @Override
    public V value(K key) {
        log.trace("value: {}", key);
        return sync(() -> container.get(key));
    }

    @Override
    public Collection<K> keys() {
        log.trace("keys");
        return sync(() -> new ArrayList<>(container.keySet()));
    }
}
