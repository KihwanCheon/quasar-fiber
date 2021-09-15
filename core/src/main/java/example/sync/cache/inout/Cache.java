package example.sync.cache.inout;

import java.util.Collection;
import java.util.Map;

public interface Cache <K, V> {

    int size();

    Map<K, V> clear();

    void put(K k, V v);

    V remove(K k);

    V value(K key);

    Collection<K> keys();
}
