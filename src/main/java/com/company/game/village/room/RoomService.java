package com.company.game.village.room;

import com.company.game.village.day.DayVoteService;
import com.company.game.village.game.GameEngineService;
import com.company.game.village.night.NightActionService;
import com.company.game.village.user.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

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
                .orElseThrow(() -> new RuntimeException("Oda bulunamadı"));

        if (!room.getOwner().getUsername().equals(adminUsername)) {
            throw new RuntimeException("Sadece oda sahibi oyuncuları atabilir");
        }

        RoomPlayer targetPlayer = roomPlayerRepository.findByRoomIdAndUserUsername(roomId, targetUsername)
                .orElseThrow(() -> new RuntimeException("Oyuncu bulunamadı"));

        if (targetPlayer.getUser().getUsername().equals(adminUsername)) {
            throw new RuntimeException("Kendini atamazsın");
        }

        //if (!List.of(WAITING, ENDED).contains(room.getCurrentPhase())) {
            //throw new RuntimeException("Oyun sırasında oyuncu atılamaz");
        //}

        roomPlayerRepository.delete(targetPlayer);
    }


    public void deleteRoom(UUID roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Oda bulunamadı"));

        if (!Optional.ofNullable(user.getIsAdmin()).orElse(false) && !room.getOwner().getUsername().equals(user.getUsername())) {
            throw new RuntimeException("Sadece oda sahibi veya admin odayı silebilir");
        }

        //if (!List.of(WAITING, ENDED).contains(room.getCurrentPhase())) {
          //  throw new RuntimeException("Oyun sırasında oda silinemez");
        //}

        roomRepository.delete(room);
    }


    /**
     * Yeni bir oda oluşturur ve oda sahibini odaya ekler.
     */
    public Room createRoom(String name, int maxPlayers, String joinKey, User owner) {
        List<Room> ownerRooms = roomRepository.findByOwner(owner);
        if (!ownerRooms.isEmpty() && !Optional.ofNullable(owner.getIsAdmin()).orElse(false)) {
            throw new RuntimeException("Zaten bir odanız var. Yeni bir oda oluşturmak için mevcut odanızı silin.");
        }

        if (roomRepository.findAll().size() >= 10 && !Optional.ofNullable(owner.getIsAdmin()).orElse(false)) {
            throw new RuntimeException("Maksimum oda sayısına ulaşıldı. Lütfen daha sonra tekrar deneyin.");
        }

        if (roomRepository.findByName(name).isPresent()) {
            throw new RuntimeException("Bu isimde zaten bir oda var");
        }

        if (maxPlayers < 5 || maxPlayers > 20) {
            throw new RuntimeException("Oyuncu sayısı 5 ile 20 arasında olmalıdır");
        }

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
            case "end-game":
                room.setCurrentPhase(ENDED);
                break;
            case "start-game":
                if (room.getPlayers().size() < 5) {
                    throw new RuntimeException("Oyuncu sayısı 5'ten az, oyun başlatılamaz");
                }
                room.setCurrentPhase(DAY);
                room.getMessages().clear();
                room.getMessagesOld().clear();
                room.getPlayers().forEach(RoomPlayer::clearMessages);
                assignRoles(room); // Burada rol atıyoruz
                roomPlayerRepository.saveAll(room.getPlayers());
                break;
            case "end-night":
                room.setCurrentPhase(DAY);
                room.setMessagesOld(room.getMessages());
                room.setMessages(new ArrayList<>());
                room.getPlayers().forEach(RoomPlayer::resetMessages);
                gameEngineService.processNight(roomId);
                room.getMessages().add("Gece bitti. Gündüz başladı!");
                room.getPlayers().forEach(RoomPlayer::resetVote);
                String winner = gameEngineService.checkWinner(roomId);
                if (!winner.equals("Oyun devam ediyor")) {
                    room.getMessages().add("Oyun Sonucu: " + winner);
                    room.setCurrentPhase(ENDED);
                    room.setWinners(winner);
                }
                break;
            case "end-day":
                room.setCurrentPhase(NIGHT);
                room.setMessagesOld(room.getMessages());
                room.setMessages(new ArrayList<>());
                room.getPlayers().forEach(RoomPlayer::resetMessages);
                gameEngineService.processDay(roomId);
                room.getMessages().add("Gündüz bitti. Gece başladı!");
                room.getPlayers().forEach(RoomPlayer::resetVote);
                String winner2 = gameEngineService.checkWinner(roomId);
                if (!winner2.equals("Oyun devam ediyor")) {
                    room.getMessages().add("Oyun Sonucu: " + winner2);
                    room.setCurrentPhase(ENDED);
                    room.setWinners(winner2);
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
        roles.add(HUNTER);
        roles.add(VAMPIRE);

        for (int i = 0; i < (playerCount / 8); i++) {
            roles.add(VAMPIRE);
        }
        for (int i = roles.size(); i < playerCount; i++) {
            roles.add(VILLAGER);
        }

        Collections.shuffle(roles);
        Collections.shuffle(players);

        for (int i = 0; i < playerCount; i++) {
            RoomPlayer player = players.get(i);
            player.setRole(roles.get(i));
            player.reset();
        }

        for (int i = 0; i < playerCount; i++) {
            RoomPlayer player = players.get(i);
            List<RoomPlayer> suspiciousPlayers = new ArrayList<>(players).stream()
                    .filter(p -> p.getRole() != VAMPIRE)
                    .filter(p -> p.getId() != player.getId())
                    .collect(Collectors.toList());
            Collections.shuffle(suspiciousPlayers);

            RoomPlayer vampire = players.stream()
                    .filter(p -> p.getRole() == VAMPIRE)
                    .findFirst()
                    .orElseThrow();

            List<RoomPlayer> suspicious = new ArrayList<>(suspiciousPlayers.subList(0, 3));
            suspicious.add(0, vampire);
            Collections.shuffle(suspicious);

            // Add message to player
            if (player.getRole().equals(VAMPIRE)) {
                if (players.stream().filter(p -> p.getRole().equals(VAMPIRE)).count() > 1) {
                    String message = String.format("Köydeki vampirler: %s.", players.stream().filter(p -> p.getRole().equals(VAMPIRE)).map(p -> p.getUser().getUsername()).collect(Collectors.joining(", ")));
                    player.addMessage(message);
                }
            } else {
                // Replaced simple fixed message with richer, randomized templates
                String message = generateWhisperMessage(player, suspicious);
                player.addMessage(message);
            }
        }

    }

    // Generate a richer, slightly randomized Turkish whisper message for non-vampire players
    private String generateWhisperMessage(RoomPlayer player, List<RoomPlayer> suspects) {
        // use player to avoid unused parameter warning and to personalize fallback message
        String you = player != null && player.getUser() != null ? player.getUser().getUsername() : "sensin";

        if (suspects == null || suspects.size() < 2) {
            return String.format("Gece fısıltılarında %s'in kulağına birkaç isim takıldı; aklında soru işaretleri belirdi.", you);
        }

        String a = suspects.get(0).getUser().getUsername();
        String b = suspects.get(2).getUser().getUsername();

        String[] templates = new String[]{
                "Gece fısıltılarında %s ve %s adları sıkça geçiyordu; kulağına bir sır düştü.",
                "Karanlıkta %s ile %s'in konuştuğunu duydun; ses tonları tedirgin ediciydi.",
                "Uzakta, %s ve %s arasında geçen konuşma seni endişelendirdi; bir şeyler saklıyor gibiydiler.",
                "%s ve %s isimlerini fısıltı halinde duydun; davranışları şüphe uyandırdı.",
                "Gecenin sessizliğinde %s ile %s konuşurken yakalandılar; içgüdün hemen harekete geçti."
        };

        int idx = ThreadLocalRandom.current().nextInt(templates.length);
        return String.format(templates[idx], a, b);
    }

    public Room performAction(UUID roomId, String username, ActionRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Oda bulunamadı"));

        RoomPlayer actor = roomPlayerRepository.findByRoomIdAndUserUsername(room.getId(), username)
                .orElseThrow(() -> new RuntimeException("Oyuncu bulunamadı"));

        RoomPlayer target = roomPlayerRepository.findByRoomIdAndUserUsername(room.getId(), request.getTargetUsername())
                .orElse(null);

        if (actor.getVoted()) {
            throw new RuntimeException("Zaten oy kullandınız");
        }

        actor.setVoted(true);
        switch (request.getAction()) {
            case "vote" -> {
                if (actor.isAlive() && target != null) {
                    actor.addMessage("Sen " + target.getUser().getUsername() + " kişisini suçladın.");
                    dayVoteService.vote(roomId, actor.getUser().getId(), target.getUser().getId());
                }
            }
            case "watch" -> {
                if (actor.isAlive() && target != null) {
                    actor.addMessage("Sen " + target.getUser().getUsername() + " kişisini izliyorsun.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                }
            }
            case "protect" -> {
                if (actor.getRole() == HUNTER && target != null) {
                    target.setProtectedByVillager(true);
                    roomPlayerRepository.save(target);
                    actor.addMessage("Avcı " + target.getUser().getUsername() + " kişisini korumaya aldın.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), PROTECT);
                }
            }
            case "kill" -> {
                if (actor.getRole() == VAMPIRE && target != null) {
                    actor.addMessage(target.getUser().getUsername() + " kişisine saldırdın.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), KILL);
                }
            }
            case "hunt" -> {
                if (actor.getRole() == HUNTER && target != null) {
                    actor.addMessage(target.getUser().getUsername() + " kişisini avladın.");
                    nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), HUNT);
                }
            }
            case "inspect" -> {
                if (actor.getRole() == SEER && target != null) {
                    if (!actor.isHasUsedSeerAction()) {
                        actor.setHasUsedSeerAction(true);
                        roomPlayerRepository.save(actor);
                        actor.addMessage(target.getUser().getUsername() + " kişisini sorguladın.");
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), SCRY);
                    } else {
                        actor.addMessage("Kahinlik yeteneğin bitti, " + target.getUser().getUsername() + " kişisini izliyorsun.");
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                    }
                }
            }
            case "poison" -> {
                if (actor.getRole() == WITCH && target != null) {
                    if (!actor.isHasUsedWitchPotion()) {
                        target.setPoisonedByWitch(true);
                        actor.setHasUsedWitchPotion(true);
                        actor.addMessage(target.getUser().getUsername() + " kişisini zehirledin.");
                        roomPlayerRepository.save(actor);
                        roomPlayerRepository.save(target);
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), POISON);
                    } else {
                        actor.addMessage("Cadılık iksirin bitti, " + target.getUser().getUsername() + " kişisini izliyorsun.");
                        nightService.addAction(roomId, actor.getUser().getId(), target.getUser().getId(), WATCH);
                    }
                }
            }
            default -> throw new RuntimeException("Geçersiz aksiyon");
        }

        return roomRepository.save(room);
    }
}
