package com.nexus.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ruta que abre cada aviso al tocar la push ([push-avisos] U3). */
class PushRouteTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "INVITE", "JOINED", "CANCELLED", "FILTERS", "READY", "OPTIONS", "MATCH",
        "PROPOSAL", "DECLINED", "AGREED", "MESSAGES", "REMINDER", "EXPIRED", "CLOSED"})
    @DisplayName("should open the planning session with its id for every planning notice")
    void shouldOpenThePlanningSessionWithItsIdForEveryPlanningNotice(String type) {
        Map<String, String> data = PushRoute.dataFor(type, 7L, null);

        assertThat(data)
                .containsEntry("type", type)
                .containsEntry("route", "/(planning)/session")
                .containsEntry("sessionId", "7");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        NoticeTypes.EVENT_PENDING, NoticeTypes.EVENT_CONFIRMED,
        NoticeTypes.EVENT_REJECTED, NoticeTypes.EVENT_DELETED})
    @DisplayName("should open the notices screen without a session for every event notice")
    void shouldOpenTheNoticesScreenWithoutASessionForEveryEventNotice(String type) {
        Map<String, String> data = PushRoute.dataFor(type, null, null);

        assertThat(data)
                .containsEntry("route", "/(events)/notifications")
                .doesNotContainKey("sessionId");
    }

    @Test
    @DisplayName("should open home and keep the partner name when the link is established")
    void shouldOpenHomeAndKeepThePartnerNameWhenTheLinkIsEstablished() {
        Map<String, String> data = PushRoute.dataFor(
                NoticeTypes.LINK_ESTABLISHED, null, Map.of("partnerName", "Andrea"));

        assertThat(data)
                .containsEntry("type", "LINK_ESTABLISHED")
                .containsEntry("route", "/(tabs)")
                .containsEntry("partnerName", "Andrea");
    }

    @Test
    @DisplayName("should open the link screen when the link is deleted")
    void shouldOpenTheLinkScreenWhenTheLinkIsDeleted() {
        assertThat(PushRoute.dataFor(NoticeTypes.LINK_DELETED, null, Map.of("partnerName", "Andrea")))
                .containsEntry("route", "/(tabs)/link")
                .containsEntry("partnerName", "Andrea");
    }

    @Test
    @DisplayName("[avisos-calendario] should open the free-time view of the calendar")
    void shouldOpenTheFreeTimeViewOfTheCalendar() {
        assertThat(PushRoute.dataFor(NoticeTypes.FREE_TIME, null, Map.of("freeTogether", "1")))
                .containsEntry("route", "/(tabs)/calendario")
                .containsEntry("freeTogether", "1");
    }

    @Test
    @DisplayName("[avisos-calendario] should open the Nexus event that changed on the phone")
    void shouldOpenTheNexusEventThatChangedOnThePhone() {
        assertThat(PushRoute.dataFor(NoticeTypes.SYNC_CONFLICT, null, Map.of("eventId", "21")))
                .containsEntry("route", "/(events)/edit-event")
                .containsEntry("eventId", "21");
    }

    @Test
    @DisplayName("should fall back to the notices screen for an unknown type")
    void shouldFallBackToTheNoticesScreenForAnUnknownType() {
        assertThat(PushRoute.dataFor("SOMETHING_NEW", null, null))
                .containsEntry("route", "/(events)/notifications");
    }

    @Test
    @DisplayName("should not let extra override the type or the route")
    void shouldNotLetExtraOverrideTheTypeOrTheRoute() {
        Map<String, String> data = PushRoute.dataFor(
                NoticeTypes.EVENT_DELETED, null, Map.of("type", "X", "route", "/evil"));

        assertThat(data)
                .containsEntry("type", "EVENT_DELETED")
                .containsEntry("route", "/(events)/notifications");
    }

    @Test
    @DisplayName("should keep every type within the 24 characters of the column")
    void shouldKeepEveryTypeWithinThe24CharactersOfTheColumn() {
        assertThat(java.util.List.of(
                        NoticeTypes.EVENT_PENDING, NoticeTypes.EVENT_CONFIRMED, NoticeTypes.EVENT_REJECTED,
                        NoticeTypes.EVENT_DELETED, NoticeTypes.LINK_ESTABLISHED, NoticeTypes.LINK_DELETED,
                        NoticeTypes.SYNC_CONFLICT, NoticeTypes.FREE_TIME))
                .allSatisfy(type -> assertThat(type.length()).isLessThanOrEqualTo(24));
    }
}
