package com.ticketrush.webhook.repository;

import com.ticketrush.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    boolean existsByEventId(String eventId);
}
