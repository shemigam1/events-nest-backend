-- V2: Add event short code and ticket short code columns.
--
-- events.code    — 8-char NanoID generated on event creation. Used by
--                  check-in staff to locate an event without knowing its UUID.
--
-- tickets.short_code — 8-char NanoID generated at ticket issuance. Fallback
--                      for manual entry when a QR code cannot be scanned.

ALTER TABLE `events`
    ADD COLUMN `code` varchar(12) DEFAULT NULL AFTER `id`,
    ADD UNIQUE KEY `UK_events_code` (`code`);

ALTER TABLE `tickets`
    ADD COLUMN `short_code` varchar(10) DEFAULT NULL AFTER `qr_code`,
    ADD UNIQUE KEY `UK_tickets_short_code` (`short_code`);
