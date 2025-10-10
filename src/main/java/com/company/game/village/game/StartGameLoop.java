package com.company.game.village.game;

import com.company.game.village.room.Room;
import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerService;
import com.company.game.village.room.RoomService;
import com.company.game.village.user.User;
import com.company.game.village.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.company.game.village.room.RoomPlayer.Role;

@Component
@RequiredArgsConstructor
public class StartGameLoop {
    private final GameLoopService loopService;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomPlayerService roomPlayerService;

    //    @PostConstruct
    public void start() {
        String sessionId = UUID.randomUUID().toString().substring(0, 4);
        // Oyuncular
        User u1 = userService.register("Alice" + sessionId, "alice@test.com" + sessionId, "1234", false);
        User u2 = userService.register("Bob" + sessionId, "bob@test.com" + sessionId, "1234", false);
        User u3 = userService.register("Charlie" + sessionId, "charlie@test.com" + sessionId, "1234", false);
        User u4 = userService.register("David" + sessionId, "david@test.com" + sessionId, "1234", false);
        User u5 = userService.register("Eve" + sessionId, "eve@test.com" + sessionId, "1234", false);

        // 2️⃣ Oda oluştur
        Room room = roomService.createRoom("Oda" + sessionId, 15, null, u1);

        // 3️⃣ Oyuncular join
        RoomPlayer rp1 = roomPlayerService.addPlayerToRoom(room, u1);
        RoomPlayer rp2 = roomPlayerService.addPlayerToRoom(room, u2);
        RoomPlayer rp3 = roomPlayerService.addPlayerToRoom(room, u3);
        RoomPlayer rp4 = roomPlayerService.addPlayerToRoom(room, u4);
        RoomPlayer rp5 = roomPlayerService.addPlayerToRoom(room, u5);

        // 4️⃣ Roller dağıt
        rp1.setRole(Role.VILLAGER);
        rp2.setRole(Role.VAMPIRE);
        rp3.setRole(Role.SEER);
        rp4.setRole(Role.HUNTER);
        rp5.setRole(Role.WITCH);

        roomPlayerService.update(rp1);
        roomPlayerService.update(rp2);
        roomPlayerService.update(rp3);
        roomPlayerService.update(rp4);
        roomPlayerService.update(rp5);

        System.out.println("Oyun başladı, roller dağıtıldı");

        loopService.startGame(room.getId());
    }
}

