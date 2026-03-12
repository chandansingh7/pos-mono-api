package com.pos.service;

import com.pos.dto.request.CustomerRequest;
import com.pos.dto.response.CustomerResponse;
import com.pos.entity.Customer;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Page<CustomerResponse> getAll(String search, Pageable pageable) {
        log.debug("Fetching customers — search: '{}'", search);
        if (search != null && !search.isBlank()) {
            return customerRepository.search(search, pageable).map(CustomerResponse::from);
        }
        return customerRepository.findAll(pageable).map(CustomerResponse::from);
    }

    public CustomerResponse getById(Long id) {
        log.debug("Fetching customer id: {}", id);
        return CustomerResponse.from(findById(id));
    }

    public CustomerResponse create(CustomerRequest request) {
        log.info("Creating customer — name: '{}'", request.getName());

        String email = request.getEmail();
        if (email != null) {
            email = email.trim();
            if (email.isBlank()) {
                email = null;
            }
        }

        if (email != null && customerRepository.existsByEmail(email)) {
            log.warn("[CM002] Customer email already registered: {}", email);
            throw new BadRequestException(ErrorCode.CM002);
        }
        Customer customer = Customer.builder()
                .name(request.getName())
                .email(email)
                .phone(request.getPhone())
                .updatedBy(currentUsername())
                .build();
        CustomerResponse saved = CustomerResponse.from(customerRepository.save(customer));
        log.info("Customer created — id: {}", saved.getId());
        return saved;
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        log.info("Updating customer id: {}", id);
        Customer customer = findById(id);

        String newEmail = request.getEmail();
        if (newEmail != null) {
            newEmail = newEmail.trim();
            if (newEmail.isBlank()) {
                newEmail = null;
            }
        }

        final String emailToCheck = newEmail;
        if (newEmail != null && !newEmail.equals(customer.getEmail())) {
            customerRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    log.warn("[CM002] Customer email in use: {}", emailToCheck);
                    throw new BadRequestException(ErrorCode.CM002);
                }
            });
        }
        customer.setName(request.getName());
        customer.setEmail(newEmail);
        customer.setPhone(request.getPhone());
        customer.setUpdatedBy(currentUsername());
        log.info("Customer updated — id: {}", id);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    public void delete(Long id) {
        log.info("Deleting customer id: {}", id);
        customerRepository.delete(findById(id));
        log.info("Customer id: {} deleted", id);
    }

    public com.pos.dto.response.CountStats getStats() {
        log.debug("Fetching customer stats");
        return new com.pos.dto.response.CountStats(customerRepository.count());
    }

    /**
     * Create a member card for the customer: assign a unique barcode (MC + zero-padded id).
     * Idempotent: if customer already has a card, returns current customer with existing barcode.
     */
    public CustomerResponse createMemberCard(Long id) {
        log.info("Creating member card for customer id: {}", id);
        Customer customer = findById(id);
        if (customer.getMemberCardBarcode() != null && !customer.getMemberCardBarcode().isBlank()) {
            log.debug("Customer {} already has member card: {}", id, customer.getMemberCardBarcode());
            return CustomerResponse.from(customer);
        }
        String barcode = "MC" + String.format("%010d", customer.getId());
        customer.setMemberCardBarcode(barcode);
        customer.setUpdatedBy(currentUsername());
        customerRepository.save(customer);
        log.info("Member card created — customer id: {}, barcode: {}", id, barcode);
        return CustomerResponse.from(customer);
    }

    /**
     * Look up customer by member card barcode (for POS scan at checkout).
     */
    public CustomerResponse findByMemberCardBarcode(String barcode) {
        log.debug("Looking up customer by member card barcode: {}", barcode);
        return customerRepository.findByMemberCardBarcode(barcode != null ? barcode.trim() : "")
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CM001));
    }

    private Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CM001));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
