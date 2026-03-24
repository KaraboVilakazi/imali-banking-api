package com.imali.banking.service;

import com.imali.banking.domain.entity.AuditLog;
import com.imali.banking.domain.enums.AuditAction;
import com.imali.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, UUID userId, UUID accountId, BigDecimal amount, String metadata) {
        AuditLog entry = AuditLog.builder()
                .action(action)
                .userId(userId)
                .accountId(accountId)
                .amount(amount)
                .metadata(metadata)
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, UUID userId, String metadata) {
        log(action, userId, null, null, metadata);
    }
}
