package com.eventease.repository;

import com.eventease.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByTeamId(Long teamId);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    @Query("SELECT COUNT(tm) > 0 FROM TeamMember tm WHERE tm.team.event.id = :eventId AND tm.user.id = :userId")
    boolean existsByTeamEventIdAndUserId(@Param("eventId") Long eventId, @Param("userId") Long userId);

    void deleteByTeamId(Long teamId);
}
