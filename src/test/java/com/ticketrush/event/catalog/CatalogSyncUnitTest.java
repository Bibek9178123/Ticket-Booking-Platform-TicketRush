package com.ticketrush.event.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.event.catalog.client.TicketmasterApiClient;
import com.ticketrush.event.catalog.client.TmdbApiClient;
import com.ticketrush.event.catalog.dto.CatalogSyncResultDto;
import com.ticketrush.event.catalog.dto.TmdbMovieDto;
import com.ticketrush.event.catalog.service.CatalogSyncService;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.service.SeatService;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSyncUnitTest {

    @Mock
    private TmdbApiClient tmdbApiClient;

    @Mock
    private TicketmasterApiClient ticketmasterApiClient;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeatService seatService;

    private CatalogSyncService catalogSyncService;

    private User organizer;

    @BeforeEach
    void setUp() {
        catalogSyncService = new CatalogSyncService(
                tmdbApiClient,
                ticketmasterApiClient,
                eventRepository,
                userRepository,
                seatService,
                new ObjectMapper()
        );

        organizer = new User("Organizer", "organizer@ticketrush.com", "hash", Role.ORGANIZER);
        organizer.setId(10L);
    }

    @Test
    @DisplayName("Unit: Fallback catalog sync ingests canonical movies & stadium shows with seats")
    void testCatalogSyncWithFallback() {
        when(userRepository.findByEmail("organizer@ticketrush.com")).thenReturn(Optional.of(organizer));
        when(tmdbApiClient.getNowPlayingMovies(any())).thenReturn(List.of());
        when(ticketmasterApiClient.getTrendingLiveEvents(any())).thenReturn(List.of());
        when(eventRepository.existsByExternalSourceAndExternalId(any(), any())).thenReturn(false);

        Event mockSavedEvent = new Event();
        mockSavedEvent.setId(99L);
        when(eventRepository.save(any(Event.class))).thenReturn(mockSavedEvent);

        CatalogSyncResultDto result = catalogSyncService.syncCatalog();

        assertThat(result).isNotNull();
        assertThat(result.moviesIngested()).isEqualTo(31);
        assertThat(result.liveShowsIngested()).isEqualTo(10);
        assertThat(result.totalIngested()).isEqualTo(41);

        // Verify seats generated for each event
        verify(seatService, atLeastOnce()).generateSeatsForEvent(any(Event.class), anyList());
    }

    @Test
    @DisplayName("Unit: Idempotency skips already ingested external events")
    void testCatalogSyncDeduplication() {
        when(userRepository.findByEmail("organizer@ticketrush.com")).thenReturn(Optional.of(organizer));
        when(tmdbApiClient.getNowPlayingMovies(any())).thenReturn(List.of(
                new TmdbMovieDto(693134L, "Dune: Part Two", "Overview", "/path.jpg", null, "2024-03-01", 8.4, List.of())
        ));
        when(ticketmasterApiClient.getTrendingLiveEvents(any())).thenReturn(List.of());

        // Already exists in MySQL
        when(eventRepository.existsByExternalSourceAndExternalId(eq("TMDB"), eq("693134"))).thenReturn(true);

        CatalogSyncResultDto result = catalogSyncService.syncCatalog();

        assertThat(result).isNotNull();
        assertThat(result.moviesIngested()).isEqualTo(0);
    }
}
