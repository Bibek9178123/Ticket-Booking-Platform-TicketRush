package com.ticketrush.event.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.event.catalog.client.TicketmasterApiClient;
import com.ticketrush.event.catalog.client.TmdbApiClient;
import com.ticketrush.event.catalog.dto.CatalogSyncResultDto;
import com.ticketrush.event.catalog.dto.TicketmasterEventDto;
import com.ticketrush.event.catalog.dto.TmdbMovieDto;
import com.ticketrush.event.dto.TierRule;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.service.SeatService;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

    private final TmdbApiClient tmdbApiClient;
    private final TicketmasterApiClient ticketmasterApiClient;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SeatService seatService;
    private final ObjectMapper objectMapper;

    public CatalogSyncService(
            TmdbApiClient tmdbApiClient,
            TicketmasterApiClient ticketmasterApiClient,
            EventRepository eventRepository,
            UserRepository userRepository,
            SeatService seatService,
            ObjectMapper objectMapper) {
        this.tmdbApiClient = tmdbApiClient;
        this.ticketmasterApiClient = ticketmasterApiClient;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.seatService = seatService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CatalogSyncResultDto syncCatalog() {
        return syncCatalog(null, null);
    }

    @Transactional
    public CatalogSyncResultDto syncCatalog(String tmdbKey, String ticketmasterKey) {
        User defaultOrganizer = getOrCreateDefaultOrganizer();

        int moviesIngested = syncMovies(defaultOrganizer, tmdbKey);
        int liveShowsIngested = syncLiveShows(defaultOrganizer, ticketmasterKey);
        int totalIngested = moviesIngested + liveShowsIngested;

        String message = String.format("Sync complete! Ingested %d movies and %d live shows with full seat maps.",
                moviesIngested, liveShowsIngested);
        log.info("CatalogSyncService: {}", message);

        return new CatalogSyncResultDto(moviesIngested, liveShowsIngested, totalIngested, 0, message);
    }

    private int syncMovies(User organizer, String tmdbKey) {
        // Fetch live Hindi cinema (ongoing in-theater releases + upcoming releases in India)
        List<TmdbMovieDto> hindiMovies = tmdbApiClient.getHindiLiveMovies(tmdbKey);
        // Fetch live now-playing global blockbusters
        List<TmdbMovieDto> globalMovies = tmdbApiClient.getNowPlayingMovies(tmdbKey);

        java.util.Map<Long, TmdbMovieDto> merged = new java.util.LinkedHashMap<>();
        hindiMovies.forEach(m -> merged.put(m.id(), m));
        globalMovies.forEach(m -> merged.putIfAbsent(m.id(), m));

        List<TmdbMovieDto> movies = new java.util.ArrayList<>(merged.values());

        // Fallback to high-definition canonical movie releases if API key not supplied or returned empty
        if (movies.isEmpty()) {
            movies = getFallbackMovies();
        }

        List<TierRule> movieTiers = List.of(
                new TierRule("VIP Recliner", 1, 2, BigDecimal.valueOf(18.00)),
                new TierRule("Prime", 3, 4, BigDecimal.valueOf(12.50)),
                new TierRule("Classic", 5, 5, BigDecimal.valueOf(8.50))
        );
        String tiersJson = serializeTiers(movieTiers);

        int count = 0;
        String[] cities = {"Mumbai", "Delhi-NCR", "Bangalore", "Hyderabad", "Pune"};
        String[] cinemas = {"Grand Premiere Hall", "Laser IMAX Screen 1", "CineStar VIP Lounge", "Metropolis Superplex"};

        for (int i = 0; i < movies.size(); i++) {
            TmdbMovieDto m = movies.get(i);
            String externalId = String.valueOf(m.id());

            if (eventRepository.existsByExternalSourceAndExternalId("TMDB", externalId)) {
                continue; // Skip already ingested movie
            }

            LocalDateTime showTime = LocalDateTime.now()
                    .plusDays((i % 7) + 3)
                    .withHour(19)
                    .withMinute(0)
                    .withSecond(0);

            Event event = new Event();
            event.setOrganizer(organizer);
            event.setTitle(m.title());
            event.setDescription(m.overview() != null && !m.overview().isBlank() ? m.overview() : "Exclusive cinema experience in stunning 4K laser projection.");
            event.setCategory("Movies");
            event.setCity(cities[i % cities.length]);
            event.setVenue(cinemas[i % cinemas.length]);
            event.setStartsAt(showTime);
            event.setEndsAt(showTime.plusHours(3));
            event.setRowCount(5);
            event.setSeatsPerRow(10);
            event.setTierConfigJson(tiersJson);
            event.setStatus(EventStatus.PUBLISHED);
            event.setPosterUrl(m.getFullPosterUrl());
            event.setExternalSource("TMDB");
            event.setExternalId(externalId);

            Event saved = eventRepository.save(event);
            seatService.generateSeatsForEvent(saved, movieTiers);
            count++;
        }

        return count;
    }

    private int syncLiveShows(User organizer, String ticketmasterKey) {
        List<TicketmasterEventDto> shows = ticketmasterApiClient.getTrendingLiveEvents(ticketmasterKey);

        // Fallback to high-definition global stadium events if API key not supplied or returned empty
        if (shows.isEmpty()) {
            shows = getFallbackLiveShows();
        }

        List<TierRule> liveTiers = List.of(
                new TierRule("VIP Lounge", 1, 1, BigDecimal.valueOf(150.00)),
                new TierRule("Gold Circle", 2, 3, BigDecimal.valueOf(85.00)),
                new TierRule("Standard Admission", 4, 5, BigDecimal.valueOf(45.00))
        );
        String tiersJson = serializeTiers(liveTiers);

        int count = 0;
        for (int i = 0; i < shows.size(); i++) {
            TicketmasterEventDto show = shows.get(i);
            String externalId = show.id();

            if (eventRepository.existsByExternalSourceAndExternalId("TICKETMASTER", externalId)) {
                continue; // Skip already ingested show
            }

            LocalDateTime showTime = LocalDateTime.now()
                    .plusDays((i % 10) + 4)
                    .withHour(20)
                    .withMinute(0)
                    .withSecond(0);

            Event event = new Event();
            event.setOrganizer(organizer);
            event.setTitle(show.name());
            event.setDescription("Live touring experience with state-of-the-art concert acoustics and reserved luxury seating.");
            event.setCategory(show.getCategory());
            event.setCity(show.getCityName());
            event.setVenue(show.getVenueName());
            event.setStartsAt(showTime);
            event.setEndsAt(showTime.plusHours(4));
            event.setRowCount(5);
            event.setSeatsPerRow(10);
            event.setTierConfigJson(tiersJson);
            event.setStatus(EventStatus.PUBLISHED);
            event.setPosterUrl(show.getBestImageUrl());
            event.setExternalSource("TICKETMASTER");
            event.setExternalId(externalId);

            Event saved = eventRepository.save(event);
            seatService.generateSeatsForEvent(saved, liveTiers);
            count++;
        }

        return count;
    }

    public List<TmdbMovieDto> searchMovies(String query) {
        return searchMovies(query, null);
    }

    public List<TmdbMovieDto> searchMovies(String query, String tmdbKey) {
        List<TmdbMovieDto> list = tmdbApiClient.searchMovies(query, tmdbKey);
        if (list.isEmpty()) {
            return getFallbackMovies().stream()
                    .filter(m -> m.title().toLowerCase().contains(query.toLowerCase()))
                    .toList();
        }
        return list;
    }

    public List<TicketmasterEventDto> searchShows(String query) {
        return searchShows(query, null);
    }

    public List<TicketmasterEventDto> searchShows(String query, String ticketmasterKey) {
        List<TicketmasterEventDto> list = ticketmasterApiClient.searchLiveEvents(query, ticketmasterKey);
        if (list.isEmpty()) {
            return getFallbackLiveShows().stream()
                    .filter(s -> s.name().toLowerCase().contains(query.toLowerCase()))
                    .toList();
        }
        return list;
    }

    private User getOrCreateDefaultOrganizer() {
        return userRepository.findByEmail("organizer@ticketrush.com")
                .orElseGet(() -> userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.ORGANIZER || u.getRole() == Role.ADMIN)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No organizer user found to assign ingested catalog events.")));
    }

    private String serializeTiers(List<TierRule> tiers) {
        try {
            return objectMapper.writeValueAsString(tiers);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<TmdbMovieDto> getFallbackMovies() {
        return List.of(
                new TmdbMovieDto(
                        1114513L,
                        "Stree 2: Sarkate Ka Aatank",
                        "After the events of Stree, the town of Chanderi is being haunted again. This time, women are mysteriously abducted by a terrifying headless entity known as 'Sarkata'. Vicky and his friends must unite with Stree to save their town.",
                        "/mKx5tq1d0hXUu4eJ57m1uD0kQv1.jpg",
                        "/cgKZBqaYcbaGzWfBsd9NlDksf90.jpg",
                        "2024-08-15",
                        8.2,
                        List.of(35, 27)
                ),
                new TmdbMovieDto(
                        1084199L,
                        "Singham Again",
                        "DCP Bajirao Singham embarks on a dangerous cross-border rescue mission after his wife Avni is kidnapped by a ruthless transnational syndicate. Joined by Sooryavanshi and Simmba, he initiates operation Lion Heart.",
                        "/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                        "/yDHYTjA3R0ne82UT4IKHQY9pHC9.jpg",
                        "2024-11-01",
                        7.6,
                        List.of(28, 80, 53)
                ),
                new TmdbMovieDto(
                        1092823L,
                        "Bhool Bhulaiyaa 3",
                        "Rooh Baba visits the sinister palace of Rakhtghat in West Bengal to exorcise a malevolent entity, only to encounter two vengeful spirits who both claim to be the legendary Manjulika.",
                        "/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                        "/xOMo8BRK7PfcJv9JCnx7s520Wio.jpg",
                        "2024-11-01",
                        7.9,
                        List.of(35, 27)
                ),
                new TmdbMovieDto(
                        980489L,
                        "Kalki 2898 AD",
                        "Set in a post-apocalyptic world in the year 2898 AD, a modern-day avatar of Vishnu descends to Earth to protect the world from evil forces and usher in a new era.",
                        "/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg",
                        "/euYI6eot2YsgSYIRxoIOxAjx2L3.jpg",
                        "2024-06-27",
                        8.0,
                        List.of(878, 28, 12)
                ),
                new TmdbMovieDto(
                        1022789L,
                        "Fighter",
                        "Top IAF aviators come together in the face of imminent danger to form the Air Dragons elite unit, showcasing camaraderie, brotherhood, and raw grit in aerial warfare.",
                        "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                        "/rLb2cw69QbHgFDW00wbsrc3IRjw.jpg",
                        "2024-01-25",
                        7.7,
                        List.of(28, 12, 53)
                ),
                new TmdbMovieDto(
                        872906L,
                        "Jawan",
                        "A man is driven by a personal vendetta to rectify the wrongs in society, while keeping a promise made years ago. He faces a monstrous outlaw with no fear, who's caused suffering to many.",
                        "/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
                        "/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg",
                        "2023-09-07",
                        8.1,
                        List.of(28, 53)
                ),
                new TmdbMovieDto(
                        781732L,
                        "Animal",
                        "The son of a wealthy industrialist returns after years abroad. When an assassination attempt is made on his father, he sets out on a path of relentless vengeance against his family's enemies.",
                        "/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                        "/yDHYTjA3R0ne82UT4IKHQY9pHC9.jpg",
                        "2023-12-01",
                        7.5,
                        List.of(28, 80, 18)
                ),
                new TmdbMovieDto(
                        558449L,
                        "Gladiator II",
                        "Years after witnessing the death of the revered hero Maximus, Lucius must enter the Colosseum after his home is conquered by tyrannical Emperors.",
                        "/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg",
                        "/euYI6eot2YsgSYIRxoIOxAjx2L3.jpg",
                        "2024-11-15",
                        7.8,
                        List.of(28, 12, 18)
                ),
                new TmdbMovieDto(
                        693134L,
                        "Dune: Part Two",
                        "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                        "/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                        "/xOMo8BRK7PfcJv9JCnx7s520Wio.jpg",
                        "2024-03-01",
                        8.4,
                        List.of(878, 12)
                ),
                new TmdbMovieDto(
                        533535L,
                        "Deadpool & Wolverine",
                        "A listless Wade Wilson toils in civilian life until a threat to his home world sends him reluctantly back to action alongside Wolverine.",
                        "/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                        "/yDHYTjA3R0ne82UT4IKHQY9pHC9.jpg",
                        "2024-07-26",
                        7.8,
                        List.of(28, 35, 878)
                ),
                new TmdbMovieDto(
                        872585L,
                        "Oppenheimer",
                        "The story of J. Robert Oppenheimer’s role in the development of the atomic bomb during World War II.",
                        "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                        "/rLb2cw69QbHgFDW00wbsrc3IRjw.jpg",
                        "2023-07-21",
                        8.1,
                        List.of(18, 36)
                ),
                new TmdbMovieDto(
                        76600L,
                        "Avatar: The Way of Water",
                        "Set more than a decade after the events of the first film, Jake Sully and Neytiri fight to keep their family safe in the oceanic depths of Pandora.",
                        "/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
                        "/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg",
                        "2022-12-16",
                        7.7,
                        List.of(878, 12, 28)
                ),
                new TmdbMovieDto(
                        1022789L,
                        "Inside Out 2",
                        "Teenage Riley's mind undergoes a sudden demolition to make room for unexpected new Emotions: Anxiety, Envy, Ennui, and Embarrassment.",
                        "/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                        "/stKGOmXZuuIgPRtRYpKaIjazxYL.jpg",
                        "2024-06-14",
                        7.6,
                        List.of(16, 10751, 12)
                ),
                new TmdbMovieDto(
                        945961L,
                        "Alien: Romulus",
                        "While scavenging the deep ends of a derelict space station, a group of young space colonizers come face to face with the universe's deadliest lifeform.",
                        "/b33nnKl1vvg6KGggNiY2nGfBEUV.jpg",
                        "/9SSEUrSqhljBMzRe4aBTh17rUaC.jpg",
                        "2024-08-16",
                        7.3,
                        List.of(27, 878)
                ),
                new TmdbMovieDto(
                        569094L,
                        "Spider-Man: Across the Spider-Verse",
                        "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
                        "/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
                        "/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
                        "2023-06-02",
                        8.4,
                        List.of(16, 28, 12)
                ),
                new TmdbMovieDto(
                        157336L,
                        "Interstellar",
                        "A group of explorers make use of a newly discovered wormhole to surpass the limitations on human space travel and ensure mankind's survival.",
                        "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                        "/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
                        "2014-11-07",
                        8.4,
                        List.of(12, 18, 878)
                ),
                new TmdbMovieDto(
                        27205L,
                        "Inception",
                        "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a CEO.",
                        "/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg",
                        "/s3TBrRGB1iav7gFOCNx3H31MoES.jpg",
                        "2010-07-16",
                        8.4,
                        List.of(28, 878, 12)
                ),
                new TmdbMovieDto(
                        155L,
                        "The Dark Knight",
                        "Batman faces the Joker, a criminal mastermind who seeks to plunge Gotham City into anarchy and test the limits of morality.",
                        "/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
                        "/dqK9Hag1054tghRQSqLSPoYqA5P.jpg",
                        "2008-07-18",
                        8.5,
                        List.of(18, 28, 80)
                ),
                new TmdbMovieDto(
                        361743L,
                        "Top Gun: Maverick",
                        "After thirty years, Maverick pushes the envelope as a courageous test pilot and trains a detachment of graduates for a specialized mission.",
                        "/62HCnUTziyWcpDaBO2i1DX17ljH.jpg",
                        "/AaV1YIdWKnjAIAOe8UUKBFm327v.jpg",
                        "2022-05-27",
                        8.2,
                        List.of(28, 18)
                ),
                new TmdbMovieDto(
                        414906L,
                        "The Batman",
                        "In his second year of fighting crime, Batman uncovers corruption in Gotham City that connects to his own family while facing the Riddler.",
                        "/74xTEgt7R36Fpooo50r9T25onhq.jpg",
                        "/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg",
                        "2022-03-04",
                        7.7,
                        List.of(80, 9648, 53)
                ),
                new TmdbMovieDto(
                        299534L,
                        "Avengers: Endgame",
                        "After the devastating events of Infinity War, the remaining Avengers assemble once more to reverse Thanos’ actions and restore balance.",
                        "/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
                        "/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",
                        "2019-04-26",
                        8.3,
                        List.of(12, 878, 28)
                ),
                new TmdbMovieDto(
                        889737L,
                        "Joker: Folie à Deux",
                        "While struggling with his dual identity, Arthur Fleck meets the love of his life, Harleen Quinzel, while incarcerated at Arkham State Hospital.",
                        "/aciP8Km0waTLAc1v0Li8M99qnAI.jpg",
                        "/uLqNGzJ32va4qqrmugHGKyUV3fj.jpg",
                        "2024-10-04",
                        6.8,
                        List.of(18, 80)
                ),
                new TmdbMovieDto(
                        402431L,
                        "Wicked",
                        "The untold story of the witches of Oz: Elphaba, misunderstood because of her green skin, and Glinda, exceptionally popular and ambitious.",
                        "/xDGbDeRnx4mTCqVvMhkHuAuLOg9.jpg",
                        "/5O0ekOC8n21xYgW40gZz7g6Q1dZ.jpg",
                        "2024-11-22",
                        7.5,
                        List.of(18, 14, 10749)
                ),
                new TmdbMovieDto(
                        1241982L,
                        "Moana 2",
                        "After receiving an unexpected call from her wayfinding ancestors, Moana journeys to the far seas of Oceania into dangerous, long-lost waters.",
                        "/yh64qw9mgXBvlaWDi7Q9tpUBAvH.jpg",
                        "/tElnmtQ6yz1PjN1kePNl8yMSb59.jpg",
                        "2024-11-27",
                        7.2,
                        List.of(16, 12, 10751)
                ),
                new TmdbMovieDto(
                        801688L,
                        "Kalki 2898 AD",
                        "A modern-day avatar of Vishnu descends to earth to protect the world from evil forces in a futuristic dystopian world.",
                        "/x7kW1c3Zt4bVz9w5B9bLg1b2zXk.jpg",
                        "/wPNw7Upt4R87Jz6Q6f3kY5vP9kQ.jpg",
                        "2024-06-27",
                        7.6,
                        List.of(878, 28)
                ),
                new TmdbMovieDto(
                        872906L,
                        "Jawan",
                        "A man is driven by a personal vendetta to rectify the wrongs in society, while keeping a promise made years ago to a high-ranking officer.",
                        "/5myP4AeaH4h2B9P6E2uFvV2u2nQ.jpg",
                        "/t5zCBSNOVR0ipGC0IL1LTa6pB7g.jpg",
                        "2023-09-07",
                        7.5,
                        List.of(28, 53)
                ),
                new TmdbMovieDto(
                        1114513L,
                        "Stree 2: Sarkate Ka Aatank",
                        "The town of Chanderi is being haunted again. This time, women are mysteriously abducted by a headless terrifying entity.",
                        "/m2zT3iWw3z0q1sF9K5g7bL2w8.jpg",
                        "/9l7vO3aW8bB9x4y2z0s1t3w5v7.jpg",
                        "2024-08-15",
                        7.4,
                        List.of(35, 27)
                ),
                new TmdbMovieDto(
                        579974L,
                        "RRR: Rise Roar Revolt",
                        "A tale of two legendary revolutionaries and their journey far away from home before they started fighting for their country in the 1920s.",
                        "/kdOqkn0y4x4d0i3s4L5e2f7G.jpg",
                        "/6XjBcET5A4v2D3a1k9f7b4c8d.jpg",
                        "2022-03-25",
                        8.0,
                        List.of(28, 18)
                ),
                new TmdbMovieDto(
                        781732L,
                        "Animal",
                        "A son undergoes a remarkable transformation as the bond with his father begins to fracture, leading him into a ruthless underworld war.",
                        "/hr95SvBrRCRrZpsFm0R55o6uX0.jpg",
                        "/8r2x3b5f7y9u1v4w6z8a2b5c7.jpg",
                        "2023-12-01",
                        7.1,
                        List.of(28, 80, 18)
                ),
                new TmdbMovieDto(
                        550L,
                        "Fight Club",
                        "An insomniac office worker looking for a way to change his life crosses paths with a devil-may-care soap maker and forms an underground fight club.",
                        "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                        "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
                        "1999-10-15",
                        8.4,
                        List.of(18)
                ),
                new TmdbMovieDto(
                        603L,
                        "The Matrix",
                        "Set in the 22nd century, a computer hacker learns from mysterious rebels about the true nature of his reality and his role in the war against machines.",
                        "/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg",
                        "/7c9UVPPiTPltouxShY9f056k5z6.jpg",
                        "1999-03-31",
                        8.2,
                        List.of(28, 878)
                )
        );
    }

    private List<TicketmasterEventDto> getFallbackLiveShows() {
        return List.of(
                new TicketmasterEventDto(
                        "tm_coldplay_2026",
                        "Coldplay: Music of the Spheres World Tour",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-11-20", "19:00:00", "2026-11-20T19:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("Rock"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("Wembley Stadium", new TicketmasterEventDto.TmNamed("London"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_arijit_2026",
                        "Arijit Singh Symphony Live Concert",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-11-24", "19:30:00", "2026-11-24T19:30:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("Pop"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("DY Patil Stadium", new TicketmasterEventDto.TmNamed("Mumbai"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_zakir_2026",
                        "Zakir Khan: Tathastu Live Arena Tour",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-11-28", "19:00:00", "2026-11-28T19:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Arts & Theatre"), new TicketmasterEventDto.TmNamed("Comedy"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("Indira Gandhi Arena", new TicketmasterEventDto.TmNamed("Delhi-NCR"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_icc_2026",
                        "ICC Champions Trophy: India vs Australia",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-12-05", "14:00:00", "2026-12-05T14:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Sports"), new TicketmasterEventDto.TmNamed("Cricket"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("Narendra Modi Stadium", new TicketmasterEventDto.TmNamed("Ahmedabad"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_taylor_2026",
                        "Taylor Swift: The Eras Tour Experience",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-12-12", "18:30:00", "2026-12-12T18:30:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("Pop"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("MetLife Stadium", new TicketmasterEventDto.TmNamed("New York"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_diljit_2026",
                        "Diljit Dosanjh: Dil-Luminati Arena Tour",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-12-18", "19:00:00", "2026-12-18T19:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("Punjabi Pop"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("JLN Stadium", new TicketmasterEventDto.TmNamed("New Delhi"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_trevor_2026",
                        "Trevor Noah: Off the Record Comedy Tour",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1585699324551-f6c309eedeca?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-12-22", "20:00:00", "2026-12-22T20:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Arts & Theatre"), new TicketmasterEventDto.TmNamed("Comedy"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("NCPA Tata Theatre", new TicketmasterEventDto.TmNamed("Mumbai"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_dua_2026",
                        "Dua Lipa: Radical Optimism Tour",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2026-12-26", "20:00:00", "2026-12-26T20:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("Dance Pop"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("O2 Arena", new TicketmasterEventDto.TmNamed("London"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_ipl_2026",
                        "IPL 2026 Grand Finale: CSK vs MI",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2027-01-05", "19:30:00", "2027-01-05T19:30:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Sports"), new TicketmasterEventDto.TmNamed("Cricket"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("Wankhede Stadium", new TicketmasterEventDto.TmNamed("Mumbai"), null, null)))
                ),
                new TicketmasterEventDto(
                        "tm_sunburn_2026",
                        "Sunburn Goa 2026: Electronic Dance Festival",
                        List.of(new TicketmasterEventDto.TmImage("16_9", "https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?auto=format&fit=crop&w=1024&q=80", 1024, 576)),
                        new TicketmasterEventDto.TmDates(new TicketmasterEventDto.TmStart("2027-01-10", "16:00:00", "2027-01-10T16:00:00Z")),
                        List.of(new TicketmasterEventDto.TmClassification(new TicketmasterEventDto.TmNamed("Music"), new TicketmasterEventDto.TmNamed("EDM"), null)),
                        new TicketmasterEventDto.TmEmbedded(List.of(new TicketmasterEventDto.TmVenue("Vagator Beach Arena", new TicketmasterEventDto.TmNamed("Goa"), null, null)))
                )
        );
    }
}
