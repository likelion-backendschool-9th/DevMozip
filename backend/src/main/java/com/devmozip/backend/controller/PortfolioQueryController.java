package com.devmozip.backend.controller;

import com.devmozip.backend.dto.portfolio.*;
import com.devmozip.backend.service.PortfolioQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PortfolioQueryController {
    private final PortfolioQueryService service;
    @GetMapping("/users/me/portfolios")
    public PortfolioPageResponse own(Authentication auth,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.own(email(auth),page,size);
    }
    @GetMapping("/users/{slug}/portfolios")
    public PortfolioPageResponse publicList(@PathVariable String slug,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.publicList(slug,page,size);
    }
    @GetMapping("/portfolios/{id}")
    public PortfolioDetailResponse detail(Authentication auth,@PathVariable long id){return service.detail(email(auth),id);}
    private String email(Authentication auth){
        return auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken?null:auth.getName();
    }
}
