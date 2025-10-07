package com.company.game.village.day;

import com.company.game.village.room.RoomPlayer;
import com.company.game.village.room.RoomPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DayVoteService {
    private final DayVoteRepository voteRepo;
    private final RoomPlayerRepository rpRepo;

    public DayVote vote(UUID roomId, UUID voterId, UUID voteForId) {
        RoomPlayer voter = rpRepo.findByUserIdAndRoomId(voterId, roomId).stream().findFirst().orElseThrow();
        RoomPlayer voteFor = rpRepo.findByUserIdAndRoomId(voteForId, roomId).stream().findFirst().orElseThrow();

        DayVote dv = new DayVote();
        dv.setRoom(voter.getRoom());
        dv.setVoter(voter);
        dv.setVoteFor(voteFor);

        System.out.println(voter.getUser().getUsername() + " voted for " + voteFor.getUser().getUsername());
        return voteRepo.save(dv);
    }
}

