package com.company.game.village.game;

import com.company.game.village.day.DayVote;
import com.company.game.village.day.DayVoteRepository;
import com.company.game.village.night.NightAction;
import com.company.game.village.night.NightActionRepository;
import com.company.game.village.room.Room;
import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerRepository;
import com.company.game.village.room.RoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GameEngineService {

    private final NightActionRepository nightRepo;
    private final RoomPlayerRepository rpRepo;
    private final RoomRepository roomRepo;
    private final DayVoteRepository voteRepo;

    // Gece fazını işle
    public void processNight(UUID roomId) {
        List<NightAction> actions = nightRepo.findByRoomId(roomId).stream()
                .filter(a -> !a.isResolved())
                .map(NightAction::markAsResolved)
                .toList();

        Room room = roomRepo.findById(roomId).orElseThrow();

        // 1️⃣ Köylü WATCH etkisi
        List<NightAction> watcher = actions.stream()
                .filter(a -> a.getActionType() == NightAction.ActionType.WATCH)
                .peek(a -> {
                    room.getMessages().add(a.getTarget().getUser().getUsername() + " tüm gece şüphe ile izlendi.");
                    a.setResolved(true);
                })
                .toList();

        // 2️⃣ Vampir KILL
        actions.stream()
                .filter(a -> a.getActionType() == NightAction.ActionType.KILL)
                .forEach(a -> {
                    if (watcher.stream().noneMatch(w -> w.getTarget().equals(a.getActor()) && w.getActor().equals(a.getTarget()))) {
                        if (a.getTarget().getRole().equals(RoomPlayer.Role.VAMPIRE)) {
                            a.getActor().addMessage(a.getTarget().getUser().getUsername() + " kişisi de bir vampir.");
                        } else if (a.getTarget().getRole().equals(RoomPlayer.Role.HUNTER)) {
                            a.getActor().addMessage(a.getTarget().getUser().getUsername() + " kişisi bir avcı, baltayı taşa vurdun.");
                            a.getTarget().addMessage(a.getActor().getUser().getUsername() + " kişisi bir vampir, sana saldırdı ancak sen kazandın.");
                            room.getMessages().add(a.getActor().getUser().getUsername() + " gece avcıya saldırdı ancak kaybetti.");
                            a.getTarget().setAlive(false);
                        } else {
                            a.getTarget().setAlive(false);
                            room.getMessages().add(a.getTarget().getUser().getUsername() + " gece saldırıya uğradı.");
                        }
                    }
                    a.setResolved(true);
                });

        // 3️⃣ Kahin Scry → sadece bilgi, öldürmez
        actions.stream()
                .filter(a -> a.getActionType() == NightAction.ActionType.SCRY)
                .forEach(a -> {
                    a.getActor().addMessage(a.getTarget().getUser().getUsername() + " kişisi bir " + a.getTarget().getRole() + ".");
                    a.setResolved(true);
                });

        // 4️⃣ Doktor Protect
        actions.stream()
                .filter(a -> a.getActionType() == NightAction.ActionType.PROTECT)
                .forEach(a -> {
                    a.getTarget().setAlive(true); // garantile
                    room.getMessages().add(a.getTarget().getUser().getUsername() + " gece avcının koruması altındaydı.");
                    a.setResolved(true);
                });

        // 5️⃣ Cadı Poison
        actions.stream()
                .filter(a -> a.getActionType() == NightAction.ActionType.POISON)
                .forEach(a -> {
                    a.getTarget().setAlive(false);
                    room.getMessages().add(a.getTarget().getUser().getUsername() + " gece zehirlendi.");
                    a.setResolved(true);
                });

        nightRepo.saveAll(actions);
    }

    // Gündüz linç oylaması
    public void processDay(UUID roomId) {
        Map<RoomPlayer, Long> voteCount = voteRepo.findByRoomId(roomId).stream()
                .filter(v -> !Optional.ofNullable(v.getResolved()).orElse(false))
                .map(DayVote::markAsResolved)
                .collect(Collectors.groupingBy(DayVote::getVoteFor, Collectors.counting()));

        Room room = roomRepo.findById(roomId).orElseThrow();
        // Çoğunluğu alan linç edilir
        voteCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> {
                    int aliveCount = room.getPlayers().stream().filter(RoomPlayer::isAlive).toList().size(); // çoğunluk kontrolü
                    boolean isMajority = aliveCount <= e.getValue() * 3; // çoğunluk kontrolü
                    if (!isMajority) {
                        room.getMessages().add("Linç için çoğunluk sağlanamadı.");
                        return;
                    }
                    RoomPlayer target = e.getKey();
                    target.setAlive(false);
                    room.getMessages().add(target.getUser().getUsername() + " rolü " + target.getRole() + " linç edildi.");
                    rpRepo.save(target);
                });
    }

    // Kazananı belirle
    public String checkWinner(UUID roomId) {
        List<RoomPlayer> alive = rpRepo.findAll().stream()
                .filter(rp -> rp.getRoom().getId().equals(roomId) && rp.isAlive())
                .toList();

        long vampireAliveCount = alive.stream().filter(rp -> rp.getRole() == RoomPlayer.Role.VAMPIRE).count();
        long villagerAliveCount = alive.stream().filter(rp -> rp.getRole() != RoomPlayer.Role.VAMPIRE).count();

        if (vampireAliveCount >= villagerAliveCount) return "Vampirler kazandı";
        if (vampireAliveCount == 0) return "Köylüler kazandı";
        return "Oyun devam ediyor";
    }
}
