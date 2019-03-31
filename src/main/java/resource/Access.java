package resource;

import javax.naming.OperationNotSupportedException;
import java.util.List;

public interface Access<T, V> {

    boolean hasAccess(V access);
    void addAccess(V access);
    void deleteAccess(V access);

    default List<V> getUserAccess(T name) throws OperationNotSupportedException {
        throw new OperationNotSupportedException();
    }
}
