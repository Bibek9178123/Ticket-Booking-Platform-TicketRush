package com.ticketrush.event.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSearchResponse(
        int page,
        List<TmdbMovieDto> results,
        @JsonProperty("total_results") int totalResults,
        @JsonProperty("total_pages") int totalPages
) {}
