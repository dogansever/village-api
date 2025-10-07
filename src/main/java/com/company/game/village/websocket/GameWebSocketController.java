package com.company.game.village.websocket;

import com.company.game.village.day.DayVoteService;
import com.company.game.village.game.GameEngineService;
import com.company.game.village.night.NightActionService;
import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final NightActionService nightService;
    private final DayVoteService dayVoteService;
    private final GameEngineService gameEngine;
    private final RoomPlayerRepository rpRepo;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/action/{roomId}")
    public void receiveAction(@DestinationVariable UUID roomId, PlayerActionMessage msg) {
        RoomPlayer actor = rpRepo.findByUserIdAndRoomId(msg.getUserId(), roomId).stream().findFirst().orElseThrow();

        switch (msg.getActionType()) {
            case WATCH, KILL, SCRY, PROTECT, POISON ->
                    nightService.addAction(roomId, msg.getUserId(), msg.getTargetId(), msg.getActionType());
        }
    }

    public void broadcastRoomUpdate(UUID roomId, String phase) {
        List<PlayerStatus> statuses = rpRepo.findAll().stream()
                .filter(rp -> rp.getRoom().getId().equals(roomId))
                .map(rp -> new PlayerStatus(rp.getUser().getUsername(), rp.getRole(), rp.isAlive()))
                .toList();

        GameUpdateMessage msg = new GameUpdateMessage();
        msg.setPhase(phase);
        msg.setPlayers(statuses);
        msg.setWinner(gameEngine.checkWinner(roomId));

        messagingTemplate.convertAndSend("/topic/room/" + roomId, msg);
    }
}

