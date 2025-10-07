package com.company.game.village.room;

import com.company.game.village.day.DayVoteService;
import com.company.game.village.game.GameEngineService;
import com.company.game.village.night.NightActionService;
import com.company.game.village.user.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.company.game.village.night.NightAction.ActionType.*;
import static com.company.game.village.room.Room.GamePhase.*;
import static com.company.game.village.room.RoomPlayer.Role;
import static com.company.game.village.room.RoomPlayer.Role.*;

@Transactional
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final GameEngineService gameEngineService;
    private final NightActionService nightService;
    private final DayVoteService dayVoteService;

    public void kickPlayer(UUID roomId, String adminUsername, String targetUsername) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getOwner().getUsername().equals(adminUsername)) {
            throw new RuntimeException("Only the room owner can kick players");
        }

        RoomPlayer targetPlayer = roomPlayerRepository.findByRoomIdAndUserUsername(roomId, targetUsername)
                .orElseThrow(() -> new RuntimeException("Target player not found in room"));

        if (targetPlayer.getUser().getUsername().equals(adminUsername)) {
            throw new RuntimeException("You cannot kick yourself");
        }

        if (!List.of(WAITING, ENDED).contains(room.getCurrentPhase())) {
            throw new RuntimeException("You cannot kick during an active game");
        }

        roomPlayerRepository.delete(targetPlayer);
    }


    public void deleteRoom(UUID roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!Optional.ofNullable(user.getIsAdmin()).orElse(false) && !room.getOwner().getUsername().equals(user.getUsername())) {
            throw new RuntimeException("Only the creator can delete this room");
        }

        if (List.of(WAITING, ENDED).contains(room.getCurrentPhase())) {
            throw new RuntimeException("You cannot delete during an active game");
        }

        roomRepository.delete(room);
    }


    /**
     * Yeni bir oda oluşturur ve oda sahibini odaya ekler.
     */
    public Room createRoom(String name, int maxPlayers, String joinKey, User owner) {
        Room room = Room.builder()
                .name(name)
                .maxPlayers(maxPlayers)
                .owner(owner)
                .joinKey(joinKey)
                .currentPhase(WAITING)
                .build();

        room = roomRepository.save(room);

        // oda oluşturulunca owner otomatik eklensin
        RoomPlayer creator = RoomPlayer.builder()
                .room(room)
                .user(owner)
                .role(VILLAGER)
                .alive(true)
                .build();
        roomPlayerRepository.save(creator);

        return room;
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public Optional<Room> getRoom(UUID id) {
        return roomRepository.findById(id);
    }

    public List<RoomPlayer> getPlayers(UUID roomId) {
        Optional<Room> room = roomRepository.findById(roomId);
        return roomPlayerRepository.findByRoom(room.orElseThrow());
    }

    public Room changePhase(UUID roomId, String phase) {
        Room room = roomRepository.findById(roomId).orElseThrow(
                () -> new RuntimeException("Oda bulunamadı")
        );

        switch (phase) {
            case "start-game":
                room.setCurrentPhase(DAY);
                room.getMessages().clear();
                room.getMessages().add("Gündüz fazı başladı!");
                assignRoles(room); // Burada rol atıyoruz
                roomPlayerRepository.saveAll(room.getPlayers());
                break;
            case "end-night":
                room.setCurrentPhase(DAY);
                room.getMessages().add("Gece fazı bitti. Gündüz başladı!");
                gameEngineService.processNight(roomId);
                room.getPlayers().forEach(RoomPlayer::resetVote);
                String winner = gameEngineService.checkWinner(roomId);
                if (!winner.equals("Oyun devam ediyor")) {
                    room.getMessages().add("Oyun Sonucu: " + winner);
                    room.setCurrentPhase(ENDED);
                }
                break;
            case "end-day":
                room.setCurrentPhase(NIGHT);
                room.getMessages().add("Gündüz fazı bitti. Gece başladı!");
                gameEngineService.processDay(roomId);
                room.getPlayers().forEach(RoomPlayer::resetVote);
                String winner2 = gameEngineService.checkWinner(roomId);
                if (!winner2.equals("Oyun devam ediyor")) {
                    room.getMessages().add("Oyun Sonucu: " + winner2);
                    room.setCurrentPhase(ENDED);
                }
                break;
            default:
                throw new RuntimeException("Geçersiz faz");
        }

        return roomRepository.save(room);
    }

    private void assignRoles(Room room) {
        List<RoomPlayer> players = room.getPlayers();
        List<Role> roles = new ArrayList<>();

        int playerCount = players.size();

        roles.add(SEER);
        roles.add(WITCH);
        roles.add(DOCTOR);

        for (int i = 0; i < (playerCount / 4); i++) {
            roles.add(VAMPIRE);
        }
        for (int i = roles.size(); i < playerCount; i++) {
            roles.add(VILLAGER);
        }

        Collections.shuffle(roles);
        Collections.shuffle(players);

        for (int i = 0; i < playerCount; i++) {
            players.get(i).setRole(roles.get(i));
            players.get(i).reset();
        }
    }

    public Room performAction(UUID roomId, String username, ActionRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Oda bulunamadı"));

        RoomPlayer actor = roomPlayerRepository.findByRoomIdAndUserUsername(room.getId(), username)
                .orElseThrow(() -> new RuntimeException("Oyuncu bulunamadı"));

        RoomPlayer target = roomPlayerRepository.findByRoomIdAndUserUsername(room.getId(), request.getTargetUsername())
                .orElse(null);

        if (actor.getVoted()) {
            actor.addMessage("Zaten oy kullandınız.");
            throw new RuntimeException("Zaten oy kullandınız");
        }

        room.getMessages().add(actor.getUser().getUsername() + " birini oyladı.");
        actor.setVoted(true);
        switch (request.getAction()) {
            case "vote" -> {
                if (actor.isAlive() && target != null) {
                    dayVoteService.vote(roomId, actor.getUser().getId(), target.getUser().getId());
                }
            }
            case "watch" -> {
                if (actor.isAlive() && target != null) {
                    actor.addMessage("Köylü " + target.getUser().getUsername() + " kişisini izliyor.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                }
            }
            case "protect" -> {
                if (actor.getRole() == DOCTOR && target != null) {
                    target.setProtectedByVillager(true);
                    roomPlayerRepository.save(target);
                    actor.addMessage("Doktor " + target.getUser().getUsername() + " kişisini korumaya aldı.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), PROTECT);
                }
            }
            case "kill" -> {
                if (actor.getRole() == VAMPIRE && target != null) {
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), KILL);
                }
            }
            case "inspect" -> {
                if (actor.getRole() == SEER && target != null) {
                    if (!actor.isHasUsedSeerAction()) {
                        actor.setHasUsedSeerAction(true);
                        roomPlayerRepository.save(actor);
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), SCRY);
                    } else {
                        actor.addMessage("Kahinin yeteneği bitti, " + target.getUser().getUsername() + " kişisini izliyor.");
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                    }
                }
            }
            case "poison" -> {
                if (actor.getRole() == WITCH && target != null) {
                    if (!actor.isHasUsedWitchPotion()) {
                        target.setPoisonedByWitch(true);
                        actor.setHasUsedWitchPotion(true);
                        roomPlayerRepository.save(actor);
                        roomPlayerRepository.save(target);
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), POISON);
                    } else {
                        actor.addMessage("Cadının iksiri bitti, " + target.getUser().getUsername() + " kişisini izliyor.");
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                    }
                }
            }
            default -> throw new RuntimeException("Geçersiz aksiyon");
        }

        return roomRepository.save(room);
    }
}