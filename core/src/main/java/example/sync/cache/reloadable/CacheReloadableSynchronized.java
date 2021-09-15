package example.sync.cache.reloadable;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class CacheReloadableSynchronized<K, V> implements CacheReloadable<K, V> {

    Map<K, V> container = new HashMap<>();

    synchronized <T> T sync(Supplier<T> supplier) {
        return supplier.get();
    }


    @Override
    public int size() {
        return sync(() -> container.size());
    }

    @Override
    public void load(Consumer<Map<K, V>> gen) {
        log.trace("load");
        sync(() -> {
            container.clear();
            gen.accept(container);
            return true;
        });
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
