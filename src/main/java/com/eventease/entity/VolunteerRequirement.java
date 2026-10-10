package com.eventease.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "volunteer_requirements")
public class VolunteerRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", unique = true, nullable = false)
    private Event event;

    private Integer requiredCount;

    @Column(length = 1000)
    private String description;

    public VolunteerRequirement() {
    }

    public VolunteerRequirement(Event event, Integer requiredCount, String description) {
        this.event = event;
        this.requiredCount = requiredCount;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public Integer getRequiredCount() {
        return requiredCount;
    }

    public void setRequiredCount(Integer requiredCount) {
        this.requiredCount = requiredCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
