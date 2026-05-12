package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.dto.response.EventAnalyticsResponse;

import java.util.UUID;

public interface EventAnalyticsService {
    EventAnalyticsResponse getAnalytics(UUID eventId, User organizer);
}
