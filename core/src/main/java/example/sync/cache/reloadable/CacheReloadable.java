package example.sync.cache.reloadable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public interface CacheReloadable<K, V> {

    int size();

    void load(Consumer<Map<K, V>> gen);

    V value(K key);

    Collection<K> keys();
}
