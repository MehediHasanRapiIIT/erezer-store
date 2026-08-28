package kn.org.deliverybackend.entity.base;

public interface Identifiable<T> {
    T getId();
    void setId(T id);
}
