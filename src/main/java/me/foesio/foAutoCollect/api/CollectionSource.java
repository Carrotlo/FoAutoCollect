package me.foesio.foAutoCollect.api;

/** The gameplay path that supplied an auto-collected reward. */
public enum CollectionSource {
    BLOCK_BREAK,
    MOB_KILL,
    FISHING,
    SHEARING,
    EXPLOSION,
    FO_DROPS,
    EXTERNAL,
    UNKNOWN
}
