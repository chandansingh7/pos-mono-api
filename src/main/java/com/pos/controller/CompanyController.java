package com.pos.controller;

import com.pos.dto.request.CompanyRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.CompanyResponse;
import com.pos.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
            @Valid @RequestBody CompanyRequest request,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        return ResponseEntity.ok(ApiResponse.ok("Company updated", companyService.update(request, username)));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> uploadLogo(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        try {
            CompanyResponse updated = companyService.uploadLogo(file, username);
            return ResponseEntity.ok(ApiResponse.ok("Logo uploaded", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<CompanyResponse>error("Logo upload failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/favicon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> uploadFavicon(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        try {
            CompanyResponse updated = companyService.uploadFavicon(file, username);
            return ResponseEntity.ok(ApiResponse.ok("Favicon uploaded", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<CompanyResponse>error("Favicon upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> verifyEmail(Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        return ResponseEntity.ok(ApiResponse.ok("Email setup verified", companyService.verifyEmail(username)));
    }
}
