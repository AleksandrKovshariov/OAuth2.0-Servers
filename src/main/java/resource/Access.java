package resource;

public interface Access<T, V> {

    boolean hasAccess(AccessType accessType, T name, V access);
    void addAccess(T name, V access);
    void deleteAccess(T name, V access);

}
