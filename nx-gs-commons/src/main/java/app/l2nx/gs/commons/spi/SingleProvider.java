package app.l2nx.gs.commons.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class SingleProvider<T> {

    private final Class<T> spi;
    private final List<T> found;

    SingleProvider(Class<T> spi, List<T> found) {
        this.spi = spi;
        this.found = found;
    }

    public @Nullable T get() {
        return found.size() == 1 ? found.get(0) : null;
    }

    public boolean absent() {
        return found.isEmpty();
    }

    public boolean ambiguous() {
        return found.size() > 1;
    }

    public String ambiguity() {
        return "Several " + spi.getSimpleName() + " implementations on the classpath: ["
                + ServiceProviders.classNames(found) + "]; keep exactly one";
    }
}
