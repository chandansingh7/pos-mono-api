package com.pos.service;

import com.pos.dto.request.CompanyRequest;
import com.pos.dto.response.CompanyResponse;
import com.pos.entity.Company;
import com.pos.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ImageStorageService imageStorageService;

    public CompanyResponse get() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse update(CompanyRequest request, String updatedBy) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName(request.getName() != null ? request.getName() : "My Store");
            return companyRepository.save(newCompany);
        });
        company.setName(request.getName());
        company.setLogoUrl(request.getLogoUrl());
        company.setFaviconUrl(request.getFaviconUrl());
        company.setAddress(request.getAddress());
        company.setPhone(request.getPhone());
        company.setEmail(request.getEmail());
        company.setTaxId(request.getTaxId());
        company.setWebsite(request.getWebsite());
        company.setReceiptFooterText(request.getReceiptFooterText());
        company.setReceiptHeaderText(request.getReceiptHeaderText());
        company.setReceiptPaperSize(request.getReceiptPaperSize() != null ? request.getReceiptPaperSize() : "80mm");
        company.setDisplayCurrency(request.getDisplayCurrency() != null && !request.getDisplayCurrency().isBlank() ? request.getDisplayCurrency().trim() : "USD");
        company.setLocale(request.getLocale() != null && !request.getLocale().isBlank() ? request.getLocale().trim() : "en-US");
        company.setPosQuickShiftControls(request.getPosQuickShiftControls() != null ? request.getPosQuickShiftControls() : Boolean.FALSE);
        // Shift behaviour rules (company-level overrides with sensible defaults)
        company.setShiftMaxDifferenceAbsolute(
                request.getShiftMaxDifferenceAbsolute() != null ? request.getShiftMaxDifferenceAbsolute() : BigDecimal.ZERO);
        company.setShiftMinOpenMinutes(
                request.getShiftMinOpenMinutes() != null ? request.getShiftMinOpenMinutes() : 0L);
        company.setShiftMaxOpenHours(
                request.getShiftMaxOpenHours() != null ? request.getShiftMaxOpenHours() : 0L);
        company.setShiftRequireSameDay(
                request.getShiftRequireSameDay() != null ? request.getShiftRequireSameDay() : Boolean.FALSE);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company settings updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse uploadLogo(MultipartFile file, String updatedBy) throws IOException {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("My Store");
            return companyRepository.save(newCompany);
        });
        String url = imageStorageService.storeCompanyFile("logo", file);
        company.setLogoUrl(url);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company logo updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse uploadFavicon(MultipartFile file, String updatedBy) throws IOException {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("My Store");
            return companyRepository.save(newCompany);
        });
        String url = imageStorageService.storeCompanyFile("favicon", file);
        company.setFaviconUrl(url);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company favicon updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }
}
