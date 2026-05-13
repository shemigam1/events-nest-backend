package group.moniepoint.eventsnestserver.comments.model;

/**
 * Reaction kind on a comment. v1 only supports LIKE — the column is wide
 * enough for more (LOVE, LAUGH, etc.) once the UX warrants them, with no
 * schema change required.
 */
public enum ReactionType {
    LIKE
}
