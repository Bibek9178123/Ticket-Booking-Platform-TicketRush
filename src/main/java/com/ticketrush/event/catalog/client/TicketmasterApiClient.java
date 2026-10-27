package com.ticketrush.event.catalog.client;

import com.ticketrush.event.catalog.dto.TicketmasterEventDto;
import com.ticketrush.event.catalog.dto.TicketmasterSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TicketmasterApiClient {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterApiClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public TicketmasterApiClient(
            RestClient.Builder builder,
            @Value("${app.ticketmaster.base-url:https://app.ticketmaster.com/discovery/v2}") String baseUrl,
            @Value("${app.ticketmaster.api-key:}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<TicketmasterEventDto> getTrendingLiveEvents() {
        return getTrendingLiveEvents(null);
    }

    public List<TicketmasterEventDto> getTrendingLiveEvents(String keyOverride) {
        String effectiveKey = (keyOverride != null && !keyOverride.isBlank()) ? keyOverride.trim() : this.apiKey;
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return List.of();
        }
        try {
            TicketmasterSearchResponse res = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/events.json")
                            .queryParam("apikey", effectiveKey)
                            .queryParam("size", 20)
                            .queryParam("sort", "relevance,desc")
                            .build())
                    .retrieve()
                    .body(TicketmasterSearchResponse.class);
            return res != null ? res.getEvents() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch trending events from Ticketmaster: {}", e.getMessage());
            return List.of();
        }
    }

    public List<TicketmasterEventDto> searchLiveEvents(String keyword) {
        return searchLiveEvents(keyword, null);
    }

    public List<TicketmasterEventDto> searchLiveEvents(String keyword, String keyOverride) {
        String effectiveKey = (keyOverride != null && !keyOverride.isBlank()) ? keyOverride.trim() : this.apiKey;
        if (effectiveKey == null || effectiveKey.isBlank() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            TicketmasterSearchResponse res = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/events.json")
                            .queryParam("apikey", effectiveKey)
                            .queryParam("keyword", keyword)
                            .queryParam("size", 10)
                            .build())
                    .retrieve()
                    .body(TicketmasterSearchResponse.class);
            return res != null ? res.getEvents() : List.of();
        } catch (Exception e) {
            log.error("Failed to search live events on Ticketmaster for keyword [{}]: {}", keyword, e.getMessage());
            return List.of();
        }
    }
}
