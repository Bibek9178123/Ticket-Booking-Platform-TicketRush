package com.ticketrush.event.repository;

import com.ticketrush.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Page<Event> findAllByOrganizerId(Long organizerId, Pageable pageable);

    java.util.Optional<Event> findByExternalSourceAndExternalId(String externalSource, String externalId);

    boolean existsByExternalSourceAndExternalId(String externalSource, String externalId);
}
