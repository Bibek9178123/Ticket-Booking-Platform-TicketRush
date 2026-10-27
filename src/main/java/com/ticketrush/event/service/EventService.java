package com.ticketrush.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.common.exception.BusinessRuleException;
import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.event.dto.CreateEventRequest;
import com.ticketrush.event.dto.EventDetailResponse;
import com.ticketrush.event.dto.EventSummaryResponse;
import com.ticketrush.event.dto.TierRule;
import com.ticketrush.event.dto.UpdateEventRequest;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.entity.SeatStatus;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.seat.service.SeatService;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSeatRepository seatRepository;
    private final UserRepository userRepository;
    private final SeatService seatService;
    private final ObjectMapper objectMapper;

    public EventService(
            EventRepository eventRepository,
            EventSeatRepository seatRepository,
            UserRepository userRepository,
            SeatService seatService,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.seatService = seatService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventDetailResponse createEvent(CreateEventRequest request, UserPrincipal principal) {
        validateEventDates(request.startsAt(), request.endsAt());

        User organizer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizer user not found"));

        String tiersJson = serializeTiers(request.tiers());

        Event event = new Event(
                organizer,
                request.title(),
                request.description(),
                request.category(),
                request.city(),
                request.venue(),
                request.startsAt(),
                request.endsAt(),
                request.rowCount(),
                request.seatsPerRow(),
                tiersJson
        );

        Event saved = eventRepository.save(event);
        int totalSeats = saved.getRowCount() * saved.getSeatsPerRow();
        return toDetailResponse(saved, totalSeats, 0L, request.tiers());
    }

    @Transactional
    public EventDetailResponse updateEvent(Long eventId, UpdateEventRequest request, UserPrincipal principal) {
        Event event = getEventAndCheckOwnership(eventId, principal);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT events can be modified. Current status: " + event.getStatus());
        }

        validateEventDates(request.startsAt(), request.endsAt());

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setCity(request.city());
        event.setVenue(request.venue());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setRowCount(request.rowCount());
        event.setSeatsPerRow(request.seatsPerRow());
        event.setTierConfigJson(serializeTiers(request.tiers()));

        Event updated = eventRepository.save(event);
        int totalSeats = updated.getRowCount() * updated.getSeatsPerRow();
        return toDetailResponse(updated, totalSeats, 0L, request.tiers());
    }

    @Transactional
    public EventDetailResponse publishEvent(Long eventId, UserPrincipal principal) {
        Event event = getEventAndCheckOwnership(eventId, principal);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessRuleException("Event cannot be published from status: " + event.getStatus());
        }

        List<TierRule> tiers = deserializeTiers(event.getTierConfigJson());
        seatService.generateSeatsForEvent(event, tiers);

        event.setStatus(EventStatus.PUBLISHED);
        Event saved = eventRepository.save(event);

        long totalSeats = seatRepository.countByEventId(saved.getId());
        long availableSeats = seatRepository.countByEventIdAndStatus(saved.getId(), SeatStatus.AVAILABLE);

        return toDetailResponse(saved, (int) totalSeats, availableSeats, tiers);
    }

    @Transactional
    public EventDetailResponse cancelEvent(Long eventId, UserPrincipal principal) {
        Event event = getEventAndCheckOwnership(eventId, principal);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Event is already cancelled");
        }

        event.setStatus(EventStatus.CANCELLED);
        Event saved = eventRepository.save(event);

        long totalSeats = seatRepository.countByEventId(saved.getId());
        long availableSeats = seatRepository.countByEventIdAndStatus(saved.getId(), SeatStatus.AVAILABLE);
        List<TierRule> tiers = deserializeTiers(saved.getTierConfigJson());

        return toDetailResponse(saved, (int) totalSeats, availableSeats, tiers);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetails(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        int totalSeats = event.getRowCount() * event.getSeatsPerRow();
        long availableSeats = seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.AVAILABLE);
        List<TierRule> tiers = deserializeTiers(event.getTierConfigJson());

        return toDetailResponse(event, totalSeats, availableSeats, tiers);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> searchEvents(
            String keyword, String city, String category,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {

        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Public search only returns PUBLISHED events
            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase().trim() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                Predicate venueMatch = cb.like(cb.lower(root.get("venue")), pattern);
                predicates.add(cb.or(titleMatch, descMatch, venueMatch));
            }

            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("city")), city.toLowerCase().trim()));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase().trim()));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return eventRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getOrganizerEvents(UserPrincipal principal, Pageable pageable) {
        return eventRepository.findAllByOrganizerId(principal.getId(), pageable).map(this::toSummaryResponse);
    }

    private Event getEventAndCheckOwnership(Long eventId, UserPrincipal principal) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        boolean isOrganizer = event.getOrganizer().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == Role.ADMIN;

        if (!isOrganizer && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to modify this event");
        }

        return event;
    }

    private void validateEventDates(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (endsAt.isBefore(startsAt)) {
            throw new BusinessRuleException("Event end time must be after start time");
        }
    }

    private String serializeTiers(List<TierRule> tiers) {
        try {
            return objectMapper.writeValueAsString(tiers);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Failed to serialize tier configuration");
        }
    }

    private List<TierRule> deserializeTiers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TierRule>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private EventDetailResponse toDetailResponse(Event e, int totalSeats, long availableSeats, List<TierRule> tiers) {
        return new EventDetailResponse(
                e.getId(),
                e.getOrganizer().getId(),
                e.getOrganizer().getName(),
                e.getTitle(),
                e.getDescription(),
                e.getCategory(),
                e.getCity(),
                e.getVenue(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getRowCount(),
                e.getSeatsPerRow(),
                e.getStatus(),
                totalSeats,
                availableSeats,
                tiers,
                e.getPosterUrl()
        );
    }

    private EventSummaryResponse toSummaryResponse(Event e) {
        int totalSeats = e.getRowCount() * e.getSeatsPerRow();
        long availableSeats = seatRepository.countByEventIdAndStatus(e.getId(), SeatStatus.AVAILABLE);

        List<TierRule> tiers = deserializeTiers(e.getTierConfigJson());
        BigDecimal minPrice = tiers.stream()
                .map(TierRule::price)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new EventSummaryResponse(
                e.getId(),
                e.getTitle(),
                e.getCategory(),
                e.getCity(),
                e.getVenue(),
                e.getStartsAt(),
                e.getEndsAt(),
                totalSeats,
                availableSeats,
                minPrice,
                e.getStatus(),
                e.getPosterUrl()
        );
    }
}
