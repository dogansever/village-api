package com.company.game.village.websocket;

import com.company.game.village.night.NightAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerActionMessage {
    private UUID userId;
    private NightAction.ActionType actionType;
    private UUID targetId;
}
