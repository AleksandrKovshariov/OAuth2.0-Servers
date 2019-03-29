package resource;

import javax.naming.OperationNotSupportedException;
import java.util.List;

public interface Access<T, V> {

    boolean hasAccess(AccessType accessType, T name, V access);
    void addAccess(T name, V access);
    void deleteAccess(T name, V access);

    default List<V> getUserAccess(T name) throws OperationNotSupportedException {
        throw new OperationNotSupportedException();
    }
}
