package group.moniepoint.eventsnestserver.exception.handler;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class EventsNestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return error(ex, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorizedException(UnauthorizedException ex) {
        return error(ex, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidEventStateException.class)
    public ResponseEntity<?> handleInvalidEventStateException(InvalidEventStateException ex) {
        return error(ex, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EventsNestException.class)
    public ResponseEntity<?> handleEventsNestException(EventsNestException ex) {
        return error(ex, HttpStatus.INTERNAL_SERVER_ERROR);
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
        return error(ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<?> error(Exception ex, HttpStatus status) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(ex.getMessage());
        response.setSuccess(false);
        response.setErrors(List.of(ex.getLocalizedMessage()));
        return new ResponseEntity<>(response, status);
    }
}
