package com.company.game.village.day;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DayVoteRepository extends JpaRepository<DayVote, UUID> {
    List<DayVote> findByRoomId(UUID roomId);
}
