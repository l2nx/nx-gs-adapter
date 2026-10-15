package app.l2nx.gs.commons.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public final class ServiceProviders {

    private ServiceProviders() {}

    /** Pins the context classloader to {@code anchor}'s loader for the scan: a host thread's loader may not see the adapter jars. */
    public static <T> List<T> loadAll(Class<T> spi, Class<?> anchor) {
        Thread thread = Thread.currentThread();
        ClassLoader saved = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(anchor.getClassLoader());
            List<T> result = new ArrayList<T>();
            for (T item : ServiceLoader.load(spi)) {
                result.add(item);
            }
            return Collections.unmodifiableList(result);
        } finally {
            thread.setContextClassLoader(saved);
        }
    }

    public static String classNames(List<?> impls) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < impls.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(impls.get(i).getClass().getName());
        }
        return sb.toString();
    }
}
