package io.eventflow.notifications.api;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

class NotificationDTOValidatorTest {
  NotificationDTOValidator validator = new NotificationDTOValidator();

  @Test
  void validDtoGetsDefaults() {
    NotificationDTO dto = new NotificationDTO();
    dto.talkId = "t1";
    dto.eventId = "e1";
    dto.type = "UPCOMING";
    dto.title = "Title";
    dto.message = "Msg";
    validator.validate(dto);
    assertNotNull(dto.id);
    assertNotNull(dto.createdAt);
  }

  @Test
  void invalidTypeRejected() {
    NotificationDTO dto = new NotificationDTO();
    dto.talkId = "t1";
    dto.eventId = "e1";
    dto.type = "BAD";
    dto.title = "Title";
    dto.message = "Msg";
    assertThrows(ConstraintViolationException.class, () -> validator.validate(dto));
  }

  @Test
  void missingFieldsRejected() {
    NotificationDTO dto = new NotificationDTO();
    dto.type = "UPCOMING";
    assertThrows(ConstraintViolationException.class, () -> validator.validate(dto));
  }
}
