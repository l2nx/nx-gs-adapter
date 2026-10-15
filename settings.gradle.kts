rootProject.name = "nx-gs-adapter"

include(
    ":nx-gs-log",
    ":nx-gs-commons",
    ":nx-gs-adapter-api",
    ":nx-gs-kafka",
    ":nx-gs-adapter-core",
    ":nx-gs-db-sync-core",
    ":nx-gs-runtime-sync-core",
    ":nx-gs-gd-sync-core",
    ":nx-gs-db-migrations",
    ":nx-gs-db-migrator-liquibase5",
)
