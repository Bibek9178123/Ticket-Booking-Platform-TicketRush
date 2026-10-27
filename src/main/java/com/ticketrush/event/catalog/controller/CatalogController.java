package com.ticketrush.event.catalog.controller;

import com.ticketrush.event.catalog.dto.CatalogSyncResultDto;
import com.ticketrush.event.catalog.dto.TicketmasterEventDto;
import com.ticketrush.event.catalog.dto.TmdbMovieDto;
import com.ticketrush.event.catalog.service.CatalogSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogSyncService catalogSyncService;

    public CatalogController(CatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<CatalogSyncResultDto> syncCatalog(
            @RequestParam(required = false) String tmdbApiKey,
            @RequestParam(required = false) String ticketmasterApiKey) {
        return ResponseEntity.ok(catalogSyncService.syncCatalog(tmdbApiKey, ticketmasterApiKey));
    }

    @GetMapping("/search-movies")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<List<TmdbMovieDto>> searchMovies(
            @RequestParam String query,
            @RequestParam(required = false) String tmdbApiKey) {
        return ResponseEntity.ok(catalogSyncService.searchMovies(query, tmdbApiKey));
    }

    @GetMapping("/search-shows")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<List<TicketmasterEventDto>> searchShows(
            @RequestParam String query,
            @RequestParam(required = false) String ticketmasterApiKey) {
        return ResponseEntity.ok(catalogSyncService.searchShows(query, ticketmasterApiKey));
    }
}
