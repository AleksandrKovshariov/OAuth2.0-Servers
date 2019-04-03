package resource;

import javax.naming.OperationNotSupportedException;
import java.util.List;
import java.util.Map;

public interface Access<T, V> {

    boolean hasAccess(V access);
    void addAccess(V access);
    void deleteAccess(V access);
    List<V> getUserAccess(T name, Map<String, String> params);
}
