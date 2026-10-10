package com.eventease.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagedResponseTest {

    @Test
    @DisplayName("Should create PagedResponse correctly from Spring Data Page")
    void testFromPage() {
        List<String> items = List.of("Event 1", "Event 2", "Event 3");
        Page<String> page = new PageImpl<>(items, PageRequest.of(0, 10), 25);

        PagedResponse<String> response = PagedResponse.fromPage(page);

        assertEquals(3, response.getContent().size());
        assertEquals("Event 1", response.getContent().get(0));
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(25L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertFalse(response.isHasPrevious());
        assertTrue(response.isHasNext());
        assertTrue(response.isFirst());
        assertFalse(response.isLast());
    }

    @Test
    @DisplayName("Should construct with constructor and allow setters")
    void testConstructorAndSetters() {
        PagedResponse<Integer> response = new PagedResponse<>(
                List.of(1, 2), 1, 2, 6L, 3, true, true, false, false
        );

        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(6L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasPrevious());
        assertTrue(response.isHasNext());
        assertFalse(response.isFirst());
        assertFalse(response.isLast());

        response.setPage(2);
        response.setLast(true);
        response.setHasNext(false);

        assertEquals(2, response.getPage());
        assertTrue(response.isLast());
        assertFalse(response.isHasNext());
    }
}
