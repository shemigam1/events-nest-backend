package group.moniepoint.eventsnestserver.exception.handler;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class EventsNestExceptionHandler {
    @ExceptionHandler(EventsNestException.class)
    public ResponseEntity<?> handleEventsNestException(EventsNestException ex) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(ex.getMessage());
        response.setSuccess(false);
        response.setErrors(List.of(ex.getLocalizedMessage()));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(BadCredentialsException ex) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(ex.getMessage());
        response.setSuccess(false);
        response.setErrors(List.of(ex.getLocalizedMessage()));
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(ex.getMessage());
        response.setSuccess(false);
        response.setErrors(List.of(ex.getLocalizedMessage()));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
