package com.company.game.village.night;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rooms/{roomId}/night")
@RequiredArgsConstructor
public class NightController {
    private final NightActionService nightService;

    @PostMapping("/koylu/watch")
    public ResponseEntity<?> watch(@PathVariable UUID roomId,
                                   @RequestParam UUID userId,
                                   @RequestParam UUID targetId) {
        nightService.addAction(roomId, userId, targetId, NightAction.ActionType.WATCH);
        return ResponseEntity.ok(Map.of("message", "İzleme kaydedildi"));
    }

    @PostMapping("/vampir/select")
    public ResponseEntity<?> kill(@PathVariable UUID roomId,
                                  @RequestParam UUID userId,
                                  @RequestParam UUID targetId) {
        nightService.addAction(roomId, userId, targetId, NightAction.ActionType.KILL);
        return ResponseEntity.ok(Map.of("message", "Vampir seçimi kaydedildi"));
    }

    @PostMapping("/kahin/scry")
    public ResponseEntity<?> scry(@PathVariable UUID roomId,
                                  @RequestParam UUID userId,
                                  @RequestParam UUID targetId) {
        nightService.addAction(roomId, userId, targetId, NightAction.ActionType.SCRY);
        return ResponseEntity.ok(Map.of("message", "Kahin sorgulama kaydedildi"));
    }

    @PostMapping("/doktor/protect")
    public ResponseEntity<?> protect(@PathVariable UUID roomId,
                                     @RequestParam UUID userId,
                                     @RequestParam UUID targetId) {
        nightService.addAction(roomId, userId, targetId, NightAction.ActionType.PROTECT);
        return ResponseEntity.ok(Map.of("message", "Doktor koruması kaydedildi"));
    }

    @PostMapping("/cadi/poison")
    public ResponseEntity<?> poison(@PathVariable UUID roomId,
                                    @RequestParam UUID userId,
                                    @RequestParam UUID targetId) {
        nightService.addAction(roomId, userId, targetId, NightAction.ActionType.POISON);
        return ResponseEntity.ok(Map.of("message", "Cadı zehirlemesi kaydedildi"));
    }
}

