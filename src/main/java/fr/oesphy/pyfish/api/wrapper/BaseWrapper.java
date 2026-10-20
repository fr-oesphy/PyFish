package fr.oesphy.pyfish.api.wrapper;

/**
 * Base class for Python-compatible wrappers.
 * Provides common functionality for all wrapper types.
 */
public abstract class BaseWrapper<T> {
    protected final T wrapped;

    protected BaseWrapper(T wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Gets the wrapped Java object.
     */
    public T unwrap() {
        return wrapped;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + wrapped + "]";
    }
}
