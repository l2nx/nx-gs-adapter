package app.l2nx.gs.adapter.api.domain.item;

/**
 * Grade of a life stone ({@code LifeStone.grade}); the engine grade token travels in {@code gradeRaw}. A stone of a
 * line without stone grades has no grade at all ({@code null}), which is not {@link #NONE}.
 */
public enum LifeStoneGrade {
    NONE,
    MID,
    HIGH,
    TOP,
    JEWELRY,
    GREAT
}
