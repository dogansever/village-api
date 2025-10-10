package com.company.game.village.game;

import com.company.game.village.day.DayVoteService;
import com.company.game.village.night.NightActionService;
import com.company.game.village.room.Room;
import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerRepository;
import com.company.game.village.room.RoomRepository;
import com.company.game.village.websocket.GameWebSocketController;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.company.game.village.night.NightAction.ActionType;

@Service
@RequiredArgsConstructor
@Transactional
public class GameLoopService {

    private final GameEngineService gameEngine;
    private final NightActionService nightService;
    private final DayVoteService dayVoteService;
    private final RoomPlayerRepository rpRepo;
    private final RoomRepository roomRepo;
    private final GameWebSocketController gameWebSocketController;

    private final Random random = new Random();

    public void startGame(UUID roomId) {
        Room room = roomRepo.findById(roomId).orElseThrow();
        room.setCurrentPhase(Room.GamePhase.DAY);
        roomRepo.save(room);

        int turn = 1;

        while (true) {
            System.out.println("\n--- TUR " + turn + " ---");

            // Alive oyuncular
            List<RoomPlayer> alivePlayers = rpRepo.findAll().stream()
                    .filter(rp -> rp.getRoom().getId().equals(roomId) && rp.isAlive())
                    .toList();

            if (alivePlayers.isEmpty()) break;

            // 1️⃣ Gece fazı aksiyonları otomatik üret
            System.out.println("Gece fazı başlıyor. Canlı oyuncular: " + alivePlayers.size());
            simulateNightActions(roomId, alivePlayers);

            // Gece fazını işle
            gameEngine.processNight(roomId);
            gameWebSocketController.broadcastRoomUpdate(roomId, "NIGHT");
            System.out.println("Gece fazı işlendi");

            String winner = gameEngine.checkWinner(roomId);
            gameWebSocketController.broadcastRoomUpdate(roomId, "RESULT");
            if (!winner.equals("Oyun devam ediyor")) {
                System.out.println("Oyun Sonucu: " + winner);
                break;
            }

            // 2️⃣ Gündüz oylaması otomatik
            alivePlayers = rpRepo.findAll().stream()
                    .filter(rp -> rp.getRoom().getId().equals(roomId) && rp.isAlive())
                    .toList();
            System.out.println("Gündüz fazı başlıyor. Canlı oyuncular: " + alivePlayers.size());
            simulateDayVotes(roomId, alivePlayers);

            // Gündüz fazını işle
            gameEngine.processDay(roomId);
            gameWebSocketController.broadcastRoomUpdate(roomId, "DAY");
            System.out.println("Gündüz fazı işlendi");

            // 3️⃣ Kazanan kontrol
            winner = gameEngine.checkWinner(roomId);
            gameWebSocketController.broadcastRoomUpdate(roomId, "RESULT");
            if (!winner.equals("Oyun devam ediyor")) {
                System.out.println("Oyun Sonucu: " + winner);
                break;
            }

            turn++;
        }

        room.setCurrentPhase(Room.GamePhase.ENDED);
        roomRepo.save(room);
        rpRepo.findAll().stream()
                .filter(rp -> rp.getRoom().getId().equals(roomId))
                .forEach(rp -> System.out.println(rp.getUser().getUsername() + " - " + rp.getRole() + " - " + (rp.isAlive() ? "Alive" : "Dead")));
    }

    // Gece aksiyonlarını rastgele simüle et
    private void simulateNightActions(UUID roomId, List<RoomPlayer> alivePlayers) {
        for (RoomPlayer rp : alivePlayers) {
            switch (rp.getRole()) {
                case VILLAGER:
                    // Köylü rastgele birini izler
                    RoomPlayer targetWatch = randomTarget(rp, alivePlayers);
                    nightService.addAction(roomId, rp.getUser().getId(), targetWatch.getUser().getId(), ActionType.WATCH);
                    break;
                case VAMPIRE:
                    RoomPlayer targetKill = randomTarget(rp, alivePlayers);
                    nightService.addAction(roomId, rp.getUser().getId(), targetKill.getUser().getId(), ActionType.KILL);
                    break;
                case SEER:
                    RoomPlayer targetScry = randomTarget(rp, alivePlayers);
                    nightService.addAction(roomId, rp.getUser().getId(), targetScry.getUser().getId(), ActionType.SCRY);
                    break;
                case HUNTER:
                    RoomPlayer targetProtect = randomTarget(rp, alivePlayers);
                    nightService.addAction(roomId, rp.getUser().getId(), targetProtect.getUser().getId(), ActionType.PROTECT);
                    break;
                case WITCH:
                    RoomPlayer targetPoison = randomTarget(rp, alivePlayers);
                    nightService.addAction(roomId, rp.getUser().getId(), targetPoison.getUser().getId(), ActionType.POISON);
                    break;
            }
        }
    }

    // Gündüz oylamasını rastgele simüle et
    private void simulateDayVotes(UUID roomId, List<RoomPlayer> alivePlayers) {
        for (RoomPlayer rp : alivePlayers) {
            RoomPlayer targetVote = randomTarget(rp, alivePlayers);
            dayVoteService.vote(roomId, rp.getUser().getId(), targetVote.getUser().getId());
        }
    }

    private RoomPlayer randomTarget(RoomPlayer actor, List<RoomPlayer> alivePlayers) {
        List<RoomPlayer> others = alivePlayers.stream()
                .filter(rp -> !rp.getId().equals(actor.getId()))
                .toList();
        return others.get(random.nextInt(others.size()));
    }
}

