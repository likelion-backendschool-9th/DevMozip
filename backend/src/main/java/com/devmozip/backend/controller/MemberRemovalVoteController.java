package com.devmozip.backend.controller;
import java.net.URI;
import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.service.MemberRemovalVoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/projects/{projectId}")
public class MemberRemovalVoteController {
	private final MemberRemovalVoteService service;
	public MemberRemovalVoteController(MemberRemovalVoteService service){this.service=service;}
	@PostMapping("/members") public ResponseEntity<MemberRemovalVoteResponse> start(@PathVariable long projectId,@Valid @RequestBody CreateMemberRemovalVoteRequest request){var result=service.start(projectId,request);return ResponseEntity.created(URI.create("/api/v1/projects/"+projectId+"/kick-votes/"+result.id())).body(result);}
	@GetMapping("/kick-votes") public List<MemberRemovalVoteResponse> list(@PathVariable long projectId){return service.list(projectId);}
	@PostMapping("/kick-votes/{voteId}/ballots") public MemberRemovalVoteResponse cast(@PathVariable long projectId,@PathVariable long voteId,@Valid @RequestBody CastMemberRemovalBallotRequest request){return service.cast(projectId,voteId,request);}
}
