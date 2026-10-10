package com.eventease.repository;

import com.eventease.entity.EventDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventDocumentRepository extends JpaRepository<EventDocument, Long> {
    List<EventDocument> findByEventId(Long eventId);

    @EntityGraph(attributePaths = {"event"})
    @Query("SELECT d FROM EventDocument d")
    List<EventDocument> findAllWithEvent();
}
