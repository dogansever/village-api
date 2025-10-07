package com.company.game.village.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameUpdateMessage {
    private String phase; // NIGHT / DAY / RESULT
    private List<PlayerStatus> players;
    private String winner; // boş ise devam ediyor
}
