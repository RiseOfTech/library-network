package io.github.riseoftech.library.catalog.copy;

/**
 * Full set of copy statuses used by the network. Transitions are guarded by {@link Copy}.
 */
public enum CopyStatus {
    ON_SHELF,
    ON_LOAN,
    ON_HOLD,
    IN_TRANSIT,
    LOST
}
