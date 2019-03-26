package resource;

public interface Accessible<T, V> {

    boolean hasAccess(Access accessType, T name, V access);
    void addAccess(T name, V access);
    void deleteAccess(T name, V access);
    
}
