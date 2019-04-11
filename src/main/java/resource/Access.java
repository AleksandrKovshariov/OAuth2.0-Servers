package resource;

import java.util.List;
import java.util.Map;

public interface Access<T, V> {

    boolean hasAccess(V access);
    void addAccess(V access) throws Exception;
    void deleteAccess(V access) throws Exception;
    List<V> getUserAccess(T name, Map<String, String> params);
}
