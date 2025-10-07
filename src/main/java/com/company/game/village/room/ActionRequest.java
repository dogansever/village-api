package com.company.game.village.room;

import lombok.Data;

@Data
public class ActionRequest {
    private String action; // "vote", "protect", "inspect", "poison"
    private String targetUsername;
}
