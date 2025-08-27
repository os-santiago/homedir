package io.eventflow.notifications.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class NotificationDTOValidator {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  public void validate(NotificationDTO dto) {
    if (dto.id == null || dto.id.isBlank()) {
      dto.id = UUID.randomUUID().toString();
    }
    if (dto.createdAt == null) {
      dto.createdAt = Instant.now().toEpochMilli();
    }
    Set<ConstraintViolation<NotificationDTO>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
