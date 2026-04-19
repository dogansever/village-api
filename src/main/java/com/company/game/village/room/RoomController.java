package com.company.game.village.room;

import com.company.game.village.user.User;
import com.company.game.village.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.company.game.village.room.Room.GamePhase.ENDED;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final RoomPlayerService roomPlayerService;
    private final UserService userService;

    @DeleteMapping("/{roomId}/kick/{username}")
    public ResponseEntity<?> kickPlayer(
            @PathVariable UUID roomId,
            @PathVariable String username,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        User user = userService.getUserFromToken(token);
        String admin = user.getUsername();
        roomService.kickPlayer(roomId, admin, username);
        return ResponseEntity.ok("Player kicked from room");
    }


    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID roomId,
                                           @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        User user = userService.getUserFromToken(token);
        roomService.deleteRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/phase")
    public ResponseEntity<Room> changePhase(@PathVariable UUID id,
                                            @RequestHeader("Authorization") String authHeader,
                                            @RequestBody PhaseRequest request) {
        String token = authHeader.replace("Bearer ", "");
        User user = userService.getUserFromToken(token);
        if (user == null) return ResponseEntity.status(401).build();
        String username = user.getUsername();
        Room room = roomService.changePhase(id, request.getPhase());
        room.getPlayers().forEach(rp -> {
            if (!rp.getUser().getUsername().equals(username) && rp.isAlive() && room.getCurrentPhase() != ENDED) {
                rp.setRole(null);
                rp.getMessages().clear();
            }
        });
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<Room> performAction(
            @PathVariable UUID id,
            @RequestBody ActionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        User user = userService.getUserFromToken(token);
        if (user == null) return ResponseEntity.status(401).build();
        String username = user.getUsername();

        Room room = roomService.performAction(id, username, request);
        room.getPlayers().forEach(rp -> {
            if (!rp.getUser().getUsername().equals(username) && rp.isAlive() && room.getCurrentPhase() != ENDED) {
                rp.setRole(null);
                rp.getMessages().clear();
            }
        });
        return ResponseEntity.ok(room);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable UUID id,
                                            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        User user = userService.getUserFromToken(token);
        if (user == null) return ResponseEntity.status(401).build();
        String username = user.getUsername();
        Optional<Room> roomOpt = roomService.getRoom(id);
        if (roomOpt.isEmpty()) return ResponseEntity.notFound().build();
        Room room = roomOpt.get();

        // Ensure the current player's RoomPlayer is the first element in the players list
        if (room.getPlayers() != null) {
            // Build ordered list: current player (if found) -> alive others -> dead others
            List<RoomPlayer> original = new ArrayList<>(room.getPlayers());
            RoomPlayer currentRp = null;
            List<RoomPlayer> alive = new ArrayList<>();
            List<RoomPlayer> dead = new ArrayList<>();

            for (RoomPlayer rp : original) {
                if (rp == null || rp.getUser() == null) continue;
                if (username.equals(rp.getUser().getUsername())) {
                    currentRp = rp;
                } else if (rp.isAlive()) {
                    alive.add(rp);
                } else {
                    dead.add(rp);
                }
            }

            // Sort alive players alphabetically by username (case-insensitive)
            alive.sort(Comparator.comparing(rp -> rp.getUser().getUsername(), String.CASE_INSENSITIVE_ORDER));

            List<RoomPlayer> ordered = new ArrayList<>();
            if (currentRp != null) ordered.add(currentRp);
            ordered.addAll(alive);
            ordered.addAll(dead);

            // Set ordered list on the room for response (do not persist)
            room.setPlayers(ordered);

            // Apply existing masking logic to the ordered list
            ordered.forEach(rp -> {
                if (!rp.getUser().getUsername().equals(username) && rp.isAlive() && room.getCurrentPhase() != ENDED) {
                    rp.setRole(null);
                    rp.getMessages().clear();
                }
            });
        }

        return ResponseEntity.ok(room);
    }

    @GetMapping
    public ResponseEntity<List<Room>> getRooms(
            @RequestHeader("Authorization") String authHeader) {
        List<Room> rooms = roomService.getAllRooms();
        rooms.forEach(room -> room.getPlayers()
                .forEach(roomPlayer -> {
                    roomPlayer.setRole(null);
                    roomPlayer.getMessages().clear();
                }));
        return ResponseEntity.ok(rooms);
    }

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody Map<String, Object> body,
                                       @RequestHeader("Authorization") String token) {

        User user = userService.getUserFromToken(token);
        if (user == null) return ResponseEntity.status(401).body(null);

        return ResponseEntity.ok(roomService.createRoom(
                (String) body.get("name"),
                (int) body.get("maxPlayers"),
                (String) body.get("joinKey"),
                user
        ));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<String> joinRoom(
            @PathVariable UUID id,
            @RequestParam(required = false) String key,
            @RequestHeader("Authorization") String token) {

        User user = userService.getUserFromToken(token);
        if (user == null)
            throw new RuntimeException("Bilinmeyen kullanıcı");

        Optional<Room> roomOpt = roomService.getRoom(id);
        if (roomOpt.isEmpty()) return ResponseEntity.notFound().build();
        Room room = roomOpt.get();

        if (room.getJoinKey() != null && !room.getJoinKey().equals(key)) {
            throw new RuntimeException("Geçersiz oda anahtarı");
        }

        if (room.getPlayers().stream().noneMatch(p -> p.getUser().getUsername().equals(user.getUsername())) && !roomPlayerService.canJoin(room))
            throw new RuntimeException("Oda dolu veya oyun başladı");

        roomPlayerService.addPlayerToRoom(room, user);

        return ResponseEntity.ok("joined");
    }

    @GetMapping("/{roomId}/players")
    public ResponseEntity<List<RoomPlayer>> players(@PathVariable UUID roomId) {
        return ResponseEntity.ok(roomService.getPlayers(roomId));
    }
}
