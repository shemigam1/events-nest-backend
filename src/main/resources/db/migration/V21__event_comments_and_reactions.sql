-- V21: Social comments + reactions on events.
--
-- Comments are flat-or-one-level-deep (parent_id self-references for replies;
-- no further nesting). Soft-deleted via deleted_at — replies remain readable
-- even when the parent is gone. Reactions are scoped to a comment + user;
-- unique constraint keeps them idempotent per type.

CREATE TABLE event_comments (
    id          uuid         PRIMARY KEY,
    event_id    uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    author_id   varchar(255) NOT NULL REFERENCES users  (id) ON DELETE CASCADE,
    parent_id   uuid         REFERENCES event_comments (id) ON DELETE CASCADE,
    body        TEXT         NOT NULL,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6),
    edited_at   timestamp(6),
    deleted_at  timestamp(6)
);

-- Hot path: list top-level comments for an event newest-first. We always
-- filter on (event_id, parent_id IS NULL, deleted_at IS NULL) so an index
-- on (event_id, created_at) covers the common sort.
CREATE INDEX idx_event_comments_event_created
    ON event_comments (event_id, created_at DESC);

-- Reply lookups by parent.
CREATE INDEX idx_event_comments_parent
    ON event_comments (parent_id)
    WHERE parent_id IS NOT NULL;

CREATE TABLE comment_reactions (
    id          uuid         PRIMARY KEY,
    comment_id  uuid         NOT NULL REFERENCES event_comments (id) ON DELETE CASCADE,
    user_id     varchar(255) NOT NULL REFERENCES users          (id) ON DELETE CASCADE,
    type        varchar(20)  NOT NULL,
    created_at  timestamp(6) NOT NULL,
    CONSTRAINT uk_comment_reaction UNIQUE (comment_id, user_id, type)
);

CREATE INDEX idx_comment_reactions_comment ON comment_reactions (comment_id);
