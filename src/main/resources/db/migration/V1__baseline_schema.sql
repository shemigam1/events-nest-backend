-- Flyway V1 baseline.
--
-- This is a snapshot of the schema produced by Hibernate's `ddl-auto=update`
-- against MySQL 8.4. From V2 onwards, all schema changes go in new
-- versioned migration files — `ddl-auto` is set to `validate` so Hibernate
-- fails fast on drift instead of silently mutating columns.
--
-- For databases that already exist (the running local/staging instance),
-- `spring.flyway.baseline-on-migrate=true` makes Flyway record this
-- baseline without re-executing it. For fresh databases, this script runs
-- and produces an identical schema to what Hibernate had been generating.
--
-- Tables are ordered to satisfy foreign-key dependencies (parents first).

-- ─── users (root: no FK dependencies) ──────────────────────────────────────
CREATE TABLE `users` (
  `id` varchar(12) NOT NULL,
  `email` varchar(255) NOT NULL,
  `first_name` varchar(255) NOT NULL,
  `last_name` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `enabled` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── events (FK → users.created_by) ────────────────────────────────────────
CREATE TABLE `events` (
  `id` binary(16) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `venue` varchar(255) NOT NULL,
  `start_time` datetime(6) NOT NULL,
  `end_time` datetime(6) NOT NULL,
  `check_in_start_time` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','DRAFT','PENDING_APPROVAL','PUBLISHED') NOT NULL,
  `rejection_reason` text,
  `has_pending_update` bit(1) NOT NULL,
  `pending_description` text,
  `created_by` varchar(12) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmpv90a1lsx9lcxsj7xjcvvsxg` (`created_by`),
  CONSTRAINT `FKmpv90a1lsx9lcxsj7xjcvvsxg` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── ticket_tiers (FK → events) ────────────────────────────────────────────
CREATE TABLE `ticket_tiers` (
  `id` binary(16) NOT NULL,
  `event_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `row_prefix` varchar(10) NOT NULL,
  `row_count` int NOT NULL,
  `seats_per_row` int NOT NULL,
  `total_capacity` int NOT NULL,
  `available_capacity` int NOT NULL,
  `version` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcy2k0vfu8olusjrh9upechb6u` (`event_id`),
  CONSTRAINT `FKcy2k0vfu8olusjrh9upechb6u` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── bookings (FK → users, events, ticket_tiers) ───────────────────────────
CREATE TABLE `bookings` (
  `id` binary(16) NOT NULL,
  `attendee_id` varchar(12) NOT NULL,
  `event_id` binary(16) NOT NULL,
  `tier_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_reference` varchar(100) DEFAULT NULL,
  `payment_status` enum('PAID','REFUNDED') NOT NULL,
  `status` enum('CANCELLED','CONFIRMED') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKelcgrlelk5arghu5h32capih4` (`attendee_id`),
  KEY `FK2ww82bk3npaiyu9oeehwtt2q3` (`event_id`),
  KEY `FKl117xs7ppi5261wbnnlyv9x9w` (`tier_id`),
  CONSTRAINT `FKelcgrlelk5arghu5h32capih4` FOREIGN KEY (`attendee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK2ww82bk3npaiyu9oeehwtt2q3` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `FKl117xs7ppi5261wbnnlyv9x9w` FOREIGN KEY (`tier_id`) REFERENCES `ticket_tiers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── tickets (FK → users, bookings, ticket_tiers) ──────────────────────────
CREATE TABLE `tickets` (
  `id` binary(16) NOT NULL,
  `attendee_id` varchar(12) NOT NULL,
  `booking_id` binary(16) NOT NULL,
  `tier_id` binary(16) NOT NULL,
  `seat_number` varchar(20) NOT NULL,
  `qr_code` varchar(255) NOT NULL,
  `status` enum('REFUNDED','USED','VALID') NOT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `checked_in_at` datetime(6) DEFAULT NULL,
  `checked_in_by` varchar(12) DEFAULT NULL,
  `checked_in_by_label` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnq5gcy8s4da316j6e4muc5rn5` (`tier_id`,`seat_number`),
  UNIQUE KEY `UK136k8tqvcn833mi3tjgqktnx2` (`qr_code`),
  KEY `FKh1s1sfgwr47615o81nccvw44g` (`attendee_id`),
  KEY `FKefja4avuu7g29t78mxifrsynb` (`booking_id`),
  KEY `FK7ktpdt6hpwl2bkaymanyjje5l` (`checked_in_by`),
  CONSTRAINT `FKh1s1sfgwr47615o81nccvw44g` FOREIGN KEY (`attendee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKefja4avuu7g29t78mxifrsynb` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`),
  CONSTRAINT `FKevooytk659hxpiw4bt0qq34cg` FOREIGN KEY (`tier_id`) REFERENCES `ticket_tiers` (`id`),
  CONSTRAINT `FK7ktpdt6hpwl2bkaymanyjje5l` FOREIGN KEY (`checked_in_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── event_memberships (FK → users, events) ────────────────────────────────
CREATE TABLE `event_memberships` (
  `id` binary(16) NOT NULL,
  `user_id` varchar(12) NOT NULL,
  `event_id` binary(16) NOT NULL,
  `role` enum('ATTENDEE','CHECKIN_STAFF','ORGANIZER') NOT NULL,
  `status` enum('ACTIVE','REMOVED') NOT NULL,
  `assigned_by` varchar(12) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbpgksatr6b9yy1xqwdj0omipg` (`user_id`,`event_id`,`role`),
  KEY `FKsig4cqt3af1nnhqo2n732ksgq` (`assigned_by`),
  KEY `FKbx36ted8ynrfacradedbnj4uq` (`event_id`),
  CONSTRAINT `FK9x1cs39mkl6rke5v99bpr6cae` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKbx36ted8ynrfacradedbnj4uq` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `FKsig4cqt3af1nnhqo2n732ksgq` FOREIGN KEY (`assigned_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── check_in_invites (FK → users, events) ─────────────────────────────────
CREATE TABLE `check_in_invites` (
  `id` binary(16) NOT NULL,
  `event_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `token_hash` varchar(255) NOT NULL,
  `status` enum('ACTIVE','EXPIRED','REVOKED') NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(12) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs6c3b8wy3l7fdudg6sebsepiq` (`token_hash`),
  KEY `FK3w11ukg4iqsxks6e8yyxpmh9a` (`created_by`),
  KEY `FK9cuonavxiqvrgqy7ndg61ag5n` (`event_id`),
  CONSTRAINT `FK3w11ukg4iqsxks6e8yyxpmh9a` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9cuonavxiqvrgqy7ndg61ag5n` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── event_edit_requests (FK → users, events) ──────────────────────────────
CREATE TABLE `event_edit_requests` (
  `id` binary(16) NOT NULL,
  `event_id` binary(16) NOT NULL,
  `submitted_by` varchar(12) NOT NULL,
  `proposed_changes` text NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `rejection_reason` text,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqbmeujdx9f6b09ftljs0482bu` (`event_id`),
  KEY `FKdaflhbm7621qmqyfvraadfvwg` (`submitted_by`),
  CONSTRAINT `FKqbmeujdx9f6b09ftljs0482bu` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `FKdaflhbm7621qmqyfvraadfvwg` FOREIGN KEY (`submitted_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── notifications (no FK constraints) ─────────────────────────────────────
CREATE TABLE `notifications` (
  `id` binary(16) NOT NULL,
  `user_id` varchar(12) NOT NULL,
  `type` enum('BOOKING_CONFIRMED','CHECKIN_SUCCESS','EVENT_APPROVED','EVENT_REJECTED') NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` text,
  `dedupe_key` varchar(100) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notifications_type_dedupe` (`type`,`dedupe_key`),
  KEY `idx_notifications_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── email_jobs (no FK constraints — outbox is intentionally decoupled) ────
CREATE TABLE `email_jobs` (
  `id` binary(16) NOT NULL,
  `to_email` varchar(255) NOT NULL,
  `job_type` enum('BOOKING_CONFIRMED','EVENT_APPROVED','EVENT_REJECTED','STAFF_INVITE') DEFAULT NULL,
  `payload_json` text,
  `staff_name` varchar(100) DEFAULT NULL,
  `raw_token` text,
  `event_title` varchar(255) DEFAULT NULL,
  `status` enum('FAILED','PENDING','SENT') NOT NULL,
  `attempts` int NOT NULL,
  `max_attempts` int NOT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL,
  `failure_reason` text,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ─── admin_invitations (no FK constraints) ─────────────────────────────────
CREATE TABLE `admin_invitations` (
  `id` binary(16) NOT NULL,
  `email` varchar(255) NOT NULL,
  `token` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKiv0q4537f0xl3nhskainc6u4g` (`email`),
  UNIQUE KEY `UKd4meqtra7ecoe7xdla6mnpwho` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
