package app.l2nx.gs.adapter.core.settings;

import app.l2nx.gs.log.NxLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordingLog implements NxLog {

    private final List<String> warnings = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<String>());

    List<String> warnings() {
        return new ArrayList<String>(warnings);
    }

    List<String> errors() {
        return new ArrayList<String>(errors);
    }

    @Override
    public void debug(String message, Object... args) {}

    @Override
    public void info(String message, Object... args) {}

    @Override
    public void warn(String message, Object... args) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Object... args) {
        errors.add(message);
    }
}
