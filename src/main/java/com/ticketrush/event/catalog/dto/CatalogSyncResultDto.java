package com.ticketrush.event.catalog.dto;

public record CatalogSyncResultDto(
        int moviesIngested,
        int liveShowsIngested,
        int totalIngested,
        int skippedDuplicates,
        String message
) {}
