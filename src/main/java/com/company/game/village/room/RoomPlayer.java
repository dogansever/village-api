package com.company.game.village.room;

import com.company.game.village.user.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Builder
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE room_player SET deleted = true WHERE id=?")
@Where(clause = "deleted = false OR deleted IS NULL")
public class RoomPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonBackReference
    @ManyToOne
    private Room room;

    @ManyToOne
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(255)")
    private Role role;

    @ElementCollection
    private List<String> messages = new ArrayList<>();

    private boolean alive = true;
    private Boolean voted = false;
    private boolean protectedByVillager = false;
    private boolean poisonedByWitch = false;
    private boolean hasUsedSeerAction = false;
    private boolean hasUsedWitchPotion = false;

    @Builder.Default
    private Boolean deleted = false;

    public void reset() {
        alive = true;
        voted = false;
        protectedByVillager = false;
        poisonedByWitch = false;
        hasUsedSeerAction = false;
        hasUsedWitchPotion = false;
        messages.clear();
    }

    public void resetVote() {
        voted = false;
    }

    public void addMessage(String s) {
        //messages.subList(0, Math.max(0, messages.size() - 1)).clear();
        messages.add(s);
    }

    public enum Role {
        VILLAGER, VAMPIRE, SEER, HUNTER, WITCH, DOCTOR
    }
}
