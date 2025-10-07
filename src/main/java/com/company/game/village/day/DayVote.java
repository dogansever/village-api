package com.company.game.village.day;

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
public class DayVote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Room room;

    @ManyToOne
    private RoomPlayer voter;

    @ManyToOne
    private RoomPlayer voteFor;

    private Boolean resolved = false;

    public DayVote markAsResolved() {
        resolved = true;
        return this;
    }
}

