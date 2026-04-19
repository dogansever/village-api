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
                // Generate richer vampire-specific message (handles lone vampir and multiple vampires)
                String message = generateVampireMessage(player, players);
                player.addMessage(message);
            } else {
                // Replaced simple fixed message with richer, randomized templates
                String message = generateWhisperMessage(player, suspicious);
                player.addMessage(message);
            }
        }

    }

    // Generate a richer, role-aware and slightly randomized Turkish whisper message for players
    private String generateWhisperMessage(RoomPlayer player, List<RoomPlayer> suspects) {
        // personalize
        String you = player != null && player.getUser() != null ? player.getUser().getUsername() : "sensin";
        Role role = player != null ? player.getRole() : null;

        int s = suspects == null ? 0 : suspects.size();
        String a = s > 0 ? suspects.get(0).getUser().getUsername() : "birileri";
        String b = s > 1 ? suspects.get(1 % s).getUser().getUsername() : a;
        String c = s > 2 ? suspects.get(2 % s).getUser().getUsername() : a;

        // If we have no suspects, return a personalized fallback to avoid unused-variable warnings
        if (s == 0) {
            return String.format("Gece fısıltılarında %s'in kulağına birkaç isim takıldı; aklında soru işaretleri belirdi.", you);
        }

        List<String> villagerTemplates = Arrays.asList(
                "Gece fısıltılarında %s ve %s adları sıkça geçiyordu; kulağına bir sır düştü.",
                "Karanlıkta %s ile %s'in konuştuğunu duydun; ses tonları tedirgin ediciydi.",
                "Uzakta, %s ve %s arasında geçen konuşma seni endişelendirdi; bir şeyler saklıyor gibiydiler.",
                "%s ve %s isimlerini fısıltı halinde duydun; davranışları şüphe uyandırdı.",
                "Gecenin sessizliğinde %s ile %s konuşurken yakalandılar; içgüdün hemen harekete geçti.",
                "Bir köşede %s, diğer köşede %s konuşuyordu; aralarında bir bağ var gibi hissettin.",
                "Fısıltıda geçen isimler: %s, %s ve daha fazlası; kulakların buna kilitlendi.",
                "Gizli bir anlaşma mı var diye düşündün; %s ve %s gözlerinin önüne geldi."
        );

        List<String> seerTemplates = Arrays.asList(
                "Rüyan gibi bir vizyon gördün: %s rolüyle ilgili ipuçları ve %s adı belirdi.",
                "Gecenin içinde bir görüntü: %s ve %s birbirine bakıyordu; sezgilerin uyanıyor.",
                "Sinematik bir sahne gibiydi; %s bir köşede dururken %s hareket ediyordu; bunu unutmayacaksın."
        );

        List<String> hunterTemplates = Arrays.asList(
                "İçgüdülerinin köşesinde %s ve %s kaldı; avcı dikkatini onlara çevirdi.",
                "%s'in hareketleri seni rahatsız etti; %s'e gözlerin takıldı.",
                "Bir iz takip ettin; sonuçta %s ve %s konuşurken bulundun."
        );

        List<String> witchTemplates = Arrays.asList(
                "Fısıltıda tuhaf bir iksirden söz ediliyordu; %s ile %s arasında bir sır vardı.",
                "Gecenin kokusu garipti; %s isimleri geçiyor, %s'in elinde bir şey vardı gibi hissettin.",
                "%s ve %s'in konuşması sihirli bir uyarı gibi yankılandı; dikkatli ol."
        );

        List<String> chosenList;
        if (role == SEER) chosenList = seerTemplates;
        else if (role == HUNTER) chosenList = hunterTemplates;
        else if (role == WITCH) chosenList = witchTemplates;
        else chosenList = villagerTemplates;

        // Add some generic suspicious templates into villagerTemplates if chosenList is villagerTemplates
        if (chosenList == villagerTemplates) {
            chosenList = new ArrayList<>(villagerTemplates);
            chosenList.add("%s, %s'i görünce yüz ifadesi değişti; bu dikkat çekiciydi.");
            chosenList.add("Birinin sakladığı bir şey var gibi: %s ve %s biraz fazlaca temkinliydi.");
            chosenList.add("Gece boyunca %s, %s ve %s isimleri kulağına çalındı; bir bağlantı olmalı.");
        }

        int idx = ThreadLocalRandom.current().nextInt(chosenList.size());
        String template = chosenList.get(idx);

        // If template expects 3 placeholders but we only have 2 distinct suspects, use c as fallback
        if (template.contains("%s, %s ve %s")) {
            return String.format(template, a, b, c);
        }

        // Some templates use two placeholders
        if (template.chars().filter(ch -> ch == '%').count() >= 4) {
            return String.format(template, a, b, c);
        }

        return String.format(template, a, b);
    }

    // Generate messages specifically for vampires. If multiple vampires exist, show teammate names and a flavor line.
    private String generateVampireMessage(RoomPlayer player, List<RoomPlayer> allPlayers) {
        String you = player != null && player.getUser() != null ? player.getUser().getUsername() : "sensin";
        List<String> teammates = allPlayers.stream()
                .filter(p -> p.getRole() == VAMPIRE)
                .filter(p -> !Objects.equals(p.getId(), player != null ? player.getId() : null))
                .map(p -> p.getUser().getUsername())
                .collect(Collectors.toList());

        // Templates for multiple vampires (coordinated messages)
        List<String> multiple = Arrays.asList(
                "Kardeş vampirler: %s. Geceyi birlikte planlayın.",
                "Ortaklarınız: %s. Birlikte hareket etmek için fısıldadınız.",
                "%s isimleriyle aynı gölgede dolaşıyorsunuz; birlikte daha güçlüsünüz."
        );

        // Templates for lone vampire (atmospheric + hint)
        List<String> lone = Arrays.asList(
                "Yalnız bir gölgesin, %s; gece senin oyunun. Dikkatli ve kurnaz ol.",
                "%s, etraf sessizleşti; tek başına hareket etmenin zamanı olabilir.",
                "Karanlıkta sadece sen varsın, %s; gizlen ve fırsatı bekle."
        );

        if (!teammates.isEmpty()) {
            String list = String.join(", ", teammates);
            int idx = ThreadLocalRandom.current().nextInt(multiple.size());
            String base = String.format(multiple.get(idx), list);

            // tactical hints for multiple vampires
            List<String> tactics = Arrays.asList(
                    "Koordine saldırı planlayın.",
                    "Bu gece sessiz kalıp hedefleri gözlemleyin.",
                    "Bir hedef seçin ve hızlı hareket edin."
            );
            String tactic = tactics.get(ThreadLocalRandom.current().nextInt(tactics.size()));
            return base + " " + tactic;
        } else {
            int idx = ThreadLocalRandom.current().nextInt(lone.size());
            String base = String.format(lone.get(idx), you);

            // tactical hints for lone vampire
            List<String> loneTactics = Arrays.asList(
                    "Tek başınasın; dikkatli ve gizli ol.",
                    "Fırsat kollayın; yalnız hareket etmenin avantajını kullanın.",
                    "Gözlerden uzak dur ve doğru anı bekle."
            );
            String tactic = loneTactics.get(ThreadLocalRandom.current().nextInt(loneTactics.size()));
            return base + " " + tactic;
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
