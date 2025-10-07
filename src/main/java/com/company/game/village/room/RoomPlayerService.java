package com.company.game.village.room;


import com.company.game.village.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.company.game.village.room.Room.GamePhase.ENDED;
import static com.company.game.village.room.Room.GamePhase.WAITING;
import static com.company.game.village.room.RoomPlayer.Role.VILLAGER;

@Service
@RequiredArgsConstructor
public class RoomPlayerService {

    private final RoomPlayerRepository repo;

    // kullanıcıyı odaya ekle
    public RoomPlayer addPlayerToRoom(Room room, User user) {
        Optional<RoomPlayer> roomPlayer = repo.findByRoomAndUser(room, user);

        if (roomPlayer.isPresent()) return roomPlayer.orElseThrow();

        RoomPlayer rp = RoomPlayer.builder()
                .room(room)
                .user(user)
                .alive(true)
                .role(VILLAGER) // role oyun başında dağıtılacaksa null bırak
                .build();

        return repo.save(rp);
    }

    // odayaki oyuncuları getir
    public List<RoomPlayer> getPlayersInRoom(Room room) {
        return repo.findByRoom(room);
    }

    // odaya katılabilir mi?
    public boolean canJoin(Room room) {
        long count = repo.countByRoom(room);
        return List.of(WAITING, ENDED).contains(room.getCurrentPhase()) && count < room.getMaxPlayers();
    }

    public boolean isPlayerInRoom(Room room, User user) {
        return repo.existsByRoomAndUser(room, user);
    }

    public void update(RoomPlayer rp) {
        repo.save(rp);
    }
}
