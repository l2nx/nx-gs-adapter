package app.l2nx.gs.adapter.core.appsessions;

import java.time.Instant;

interface AppSessionClock {

    AppSessionClock SYSTEM = new AppSessionClock() {
        @Override
        public long monotonicMs() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override
        public Instant now() {
            return Instant.now();
        }
    };

    long monotonicMs();

    Instant now();
}
