package com.devmozip.backend.controller;

import com.devmozip.backend.dto.portfolio.*;
import com.devmozip.backend.service.PortfolioMutationService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/portfolios") @RequiredArgsConstructor
public class PortfolioMutationController {
    private final PortfolioMutationService service;
    @PostMapping public ResponseEntity<PortfolioDetailResponse> create(Authentication auth,@Valid @RequestBody PortfolioWriteRequest input){
        var result=service.create(email(auth),input);return ResponseEntity.created(URI.create("/api/v1/portfolios/"+result.portfolio().id())).body(result);
    }
    @PutMapping("/{id}") public PortfolioDetailResponse update(Authentication auth,@PathVariable long id,@Valid @RequestBody PortfolioWriteRequest input){return service.update(email(auth),id,input);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication auth,@PathVariable long id){service.delete(email(auth),id);return ResponseEntity.noContent().build();}
    private String email(Authentication auth){return auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken?null:auth.getName();}
}
