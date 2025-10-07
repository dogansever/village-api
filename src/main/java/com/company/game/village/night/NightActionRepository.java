package com.company.game.village.night;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NightActionRepository extends JpaRepository<NightAction, UUID> {
    List<NightAction> findByRoomId(UUID roomId);
}
