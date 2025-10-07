package com.company.game.village.websocket;

import com.company.game.village.room.RoomPlayer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerStatus {
    private String username;
    private RoomPlayer.Role role; // isteğe bağlı, sadece self görebilir
    private boolean alive;
}
