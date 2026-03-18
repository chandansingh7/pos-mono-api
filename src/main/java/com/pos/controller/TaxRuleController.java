package com.pos.controller;

import com.pos.dto.request.TaxRuleRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.TaxRuleResponse;
import com.pos.service.TaxRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tax-rules")
@RequiredArgsConstructor
public class TaxRuleController {

    private final TaxRuleService taxRuleService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TaxRuleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(taxRuleService.getAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TaxRuleResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taxRuleService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRuleResponse>> create(@Valid @RequestBody TaxRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tax rule created", taxRuleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRuleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody TaxRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tax rule updated", taxRuleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        taxRuleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Tax rule deleted", null));
    }
}
