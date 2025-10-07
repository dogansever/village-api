package com.company.game.village.night;

import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NightActionService {
    private final NightActionRepository actionRepo;
    private final RoomPlayerRepository rpRepo;

    public NightAction addAction(UUID roomId, UUID actorId, UUID targetId, NightAction.ActionType type) {
        RoomPlayer actor = rpRepo.findByUserIdAndRoomId(actorId, roomId).stream().findFirst().orElseThrow();
        RoomPlayer target = rpRepo.findByUserIdAndRoomId(targetId, roomId).stream().findFirst().orElseThrow();

        NightAction action = new NightAction();
        action.setActor(actor);
        action.setTarget(target);
        action.setRoom(actor.getRoom());
        action.setActionType(type);
        action.setResolved(false);

        System.out.println(actor.getUser().getUsername() + " performed " + type + " on " + target.getUser().getUsername() + " who is a " + target.getRole());
        return actionRepo.save(action);
    }
}

