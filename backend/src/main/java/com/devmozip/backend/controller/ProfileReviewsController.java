package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileReviewsResponse;
import com.devmozip.backend.service.ProfileReviewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileReviewsController {
	private final ProfileReviewsService service;
	@GetMapping("/me/reviews") public ProfileReviewsResponse own(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
		return service.own(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken?null:auth.getName(),page,size);
	}
	@GetMapping("/{slug}/reviews") public ProfileReviewsResponse publicList(@PathVariable String slug,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
		return service.publicList(slug,page,size);
	}

}
