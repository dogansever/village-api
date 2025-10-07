package com.company.game.village.night;

import com.company.game.village.day.DayVote;
import com.company.game.village.room.Room;
import com.company.game.village.room.RoomPlayer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"room", "id"})
public class NightAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(cascade = CascadeType.ALL)
    private Room room;

    @ManyToOne(cascade = CascadeType.ALL)
    private RoomPlayer actor;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @ManyToOne(cascade = CascadeType.ALL)
    private RoomPlayer target;

    private boolean resolved = false;

    public NightAction markAsResolved() {
        resolved = true;
        return this;
    }
    public enum ActionType {
        WATCH, KILL, SCRY, PROTECT, POISON
    }

}

