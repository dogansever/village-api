package com.company.game.village.room;

import com.company.game.village.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE rooms SET deleted = true WHERE id=?")
@Where(clause = "deleted = false OR deleted IS NULL")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int maxPlayers;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomPlayer> players;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(255)")
    private GamePhase currentPhase;

    @ElementCollection
    private List<String> messages = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private long createdAt = System.currentTimeMillis();

    @Column(nullable = true)
    private Long startedAt;

    @Column(nullable = true)
    private Long finishedAt;

    private String joinKey;

    @Builder.Default
    private Boolean deleted = false;

    public enum GamePhase {
        WAITING,
        DAY,
        NIGHT,
        ENDED
    }
}
