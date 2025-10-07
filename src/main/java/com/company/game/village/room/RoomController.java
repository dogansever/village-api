package com.company.game.village.room;

import com.company.game.village.user.User;
import com.company.game.village.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        try {
            String token = authHeader.replace("Bearer ", "");
            User user = userService.getUserFromToken(token);
            roomService.deleteRoom(roomId, user);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(null);
        }
    }

    @PostMapping("/{id}/phase")
    public ResponseEntity<Room> changePhase(@PathVariable UUID id, @RequestBody PhaseRequest request) {
        try {
            Room room = roomService.changePhase(id, request.getPhase());
            return ResponseEntity.ok(room);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<Room> performAction(
            @PathVariable UUID id,
            @RequestBody ActionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String username = userService.getUserFromToken(token).getUsername();

        try {
            Room room = roomService.performAction(id, username, request);
            return ResponseEntity.ok(room);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable UUID id) {
        Room room = roomService.getRoom(id).orElse(null);
        if (room != null) {
            return ResponseEntity.ok(room);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Room>> getRooms() {
        List<Room> rooms = roomService.getAllRooms();
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
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        Optional<Room> roomOpt = roomService.getRoom(id);
        if (roomOpt.isEmpty()) return ResponseEntity.notFound().build();
        Room room = roomOpt.get();

        if (room.getJoinKey() != null && !room.getJoinKey().equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Geçersiz oda anahtarı");
        }

        if (room.getPlayers().stream().noneMatch(p -> p.getUser().getUsername().equals(user.getUsername())) && !roomPlayerService.canJoin(room))
            return ResponseEntity.badRequest().body("Room is full or started");

        roomPlayerService.addPlayerToRoom(room, user);

        return ResponseEntity.ok("joined");
    }

    @GetMapping("/{roomId}/players")
    public ResponseEntity<List<RoomPlayer>> players(@PathVariable UUID roomId) {
        return ResponseEntity.ok(roomService.getPlayers(roomId));
    }
}

