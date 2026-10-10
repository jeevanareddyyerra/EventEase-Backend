package com.eventease.repository;

import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EventSpecification {

    public static Specification<Event> filterEvents(String keyword, String category, EventStatus status, Boolean upcoming) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Keyword search (case-insensitive partial match across title, description, or venue)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                Predicate venueMatch = cb.like(cb.lower(root.get("venue")), pattern);
                predicates.add(cb.or(titleMatch, descMatch, venueMatch));
            }

            // 2. Category match (exact match, case-insensitive)
            if (category != null && !category.trim().isEmpty() && !category.trim().equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            // 3. Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 4. Upcoming events filter (date is today or later)
            if (Boolean.TRUE.equals(upcoming)) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), LocalDate.now()));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
