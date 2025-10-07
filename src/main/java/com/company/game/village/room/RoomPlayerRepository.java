package com.company.game.village.room;

import com.company.game.village.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, UUID> {

    List<RoomPlayer> findByRoom(Room room);

    Optional<RoomPlayer> findByRoomAndUser(Room room, User user);

    long countByRoom(Room room);

    boolean existsByRoomAndUser(Room room, User user);

    List<RoomPlayer> findByUserIdAndRoomId(UUID userId, UUID roomId);

    Optional<RoomPlayer> findByRoomIdAndUserUsername(UUID roomId, String username);
}