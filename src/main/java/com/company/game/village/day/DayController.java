package com.company.game.village.day;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rooms/{roomId}/day")
@RequiredArgsConstructor
public class DayController {
    private final DayVoteService voteService;

    @PostMapping("/vote")
    public ResponseEntity<?> vote(@PathVariable UUID roomId,
                                  @RequestParam UUID voterId,
                                  @RequestParam UUID voteForId) {
        voteService.vote(roomId, voterId, voteForId);
        return ResponseEntity.ok(Map.of("message", "Oylama kaydedildi"));
    }
}

