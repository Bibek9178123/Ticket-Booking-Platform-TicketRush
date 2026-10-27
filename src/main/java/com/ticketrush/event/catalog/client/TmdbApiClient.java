package com.ticketrush.event.catalog.client;

import com.ticketrush.event.catalog.dto.TmdbMovieDto;
import com.ticketrush.event.catalog.dto.TmdbSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TmdbApiClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbApiClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public TmdbApiClient(
            RestClient.Builder builder,
            @Value("${app.tmdb.base-url:https://api.themoviedb.org/3}") String baseUrl,
            @Value("${app.tmdb.api-key:}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<TmdbMovieDto> getNowPlayingMovies() {
        return getNowPlayingMovies(null);
    }

    public List<TmdbMovieDto> getNowPlayingMovies(String keyOverride) {
        String effectiveKey = (keyOverride != null && !keyOverride.isBlank()) ? keyOverride.trim() : this.apiKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return List.of();
        }
        java.util.List<TmdbMovieDto> allMovies = new java.util.ArrayList<>();
        try {
            // Fetch multiple pages from TMDB to ingest now playing movies
            for (int page = 1; page <= 3; page++) {
                final int p = page;
                TmdbSearchResponse res = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/movie/now_playing")
                                .queryParam("api_key", effectiveKey)
                                .queryParam("language", "en-US")
                                .queryParam("page", p)
                                .build())
                        .retrieve()
                        .body(TmdbSearchResponse.class);
                if (res != null && res.results() != null) {
                    allMovies.addAll(res.results());
                }
            }
            return allMovies;
        } catch (Exception e) {
            log.error("Failed to fetch now playing movies from TMDB: {}", e.getMessage());
            return allMovies;
        }
    }

    /**
     * Live Hindi Cinema Fetcher:
     * Pulls active ongoing theatrical releases in India and upcoming Hindi movies
     * directly from TMDB's live discover endpoint using original language 'hi' and region 'IN'.
     */
    public List<TmdbMovieDto> getHindiLiveMovies(String keyOverride) {
        String effectiveKey = (keyOverride != null && !keyOverride.isBlank()) ? keyOverride.trim() : this.apiKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return List.of();
        }
        java.util.Map<Long, TmdbMovieDto> movieMap = new java.util.LinkedHashMap<>();
        String today = java.time.LocalDate.now().toString();

        try {
            // 1. Current Ongoing Hindi Theatrical Releases in India
            for (int page = 1; page <= 2; page++) {
                final int p = page;
                TmdbSearchResponse ongoing = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/discover/movie")
                                .queryParam("api_key", effectiveKey)
                                .queryParam("with_original_language", "hi")
                                .queryParam("region", "IN")
                                .queryParam("sort_by", "popularity.desc")
                                .queryParam("page", p)
                                .build())
                        .retrieve()
                        .body(TmdbSearchResponse.class);
                if (ongoing != null && ongoing.results() != null) {
                    ongoing.results().forEach(m -> movieMap.put(m.id(), m));
                }
            }

            // 2. Upcoming Hindi Movies Releasing from Today onwards (Coming Soon)
            for (int page = 1; page <= 2; page++) {
                final int p = page;
                TmdbSearchResponse upcoming = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/discover/movie")
                                .queryParam("api_key", effectiveKey)
                                .queryParam("with_original_language", "hi")
                                .queryParam("primary_release_date.gte", today)
                                .queryParam("sort_by", "primary_release_date.asc")
                                .queryParam("page", p)
                                .build())
                        .retrieve()
                        .body(TmdbSearchResponse.class);
                if (upcoming != null && upcoming.results() != null) {
                    upcoming.results().forEach(m -> movieMap.putIfAbsent(m.id(), m));
                }
            }

            // 3. Official Indian Box-Office Now Playing
            TmdbSearchResponse nowPlayingIn = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/movie/now_playing")
                            .queryParam("api_key", effectiveKey)
                            .queryParam("region", "IN")
                            .queryParam("page", 1)
                            .build())
                    .retrieve()
                    .body(TmdbSearchResponse.class);
            if (nowPlayingIn != null && nowPlayingIn.results() != null) {
                nowPlayingIn.results().forEach(m -> movieMap.putIfAbsent(m.id(), m));
            }

        } catch (Exception e) {
            log.error("Failed to fetch live Hindi movies from TMDB: {}", e.getMessage());
        }

        return new java.util.ArrayList<>(movieMap.values());
    }

    public List<TmdbMovieDto> searchMovies(String query) {
        return searchMovies(query, null);
    }

    public List<TmdbMovieDto> searchMovies(String query, String keyOverride) {
        String effectiveKey = (keyOverride != null && !keyOverride.isBlank()) ? keyOverride.trim() : this.apiKey;
        if (effectiveKey == null || effectiveKey.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            TmdbSearchResponse res = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/movie")
                            .queryParam("api_key", effectiveKey)
                            .queryParam("query", query)
                            .queryParam("language", "en-US")
                            .build())
                    .retrieve()
                    .body(TmdbSearchResponse.class);
            return res != null && res.results() != null ? res.results() : List.of();
        } catch (Exception e) {
            log.error("Failed to search movies on TMDB for query [{}]: {}", query, e.getMessage());
            return List.of();
        }
    }
}
