package group.moniepoint.eventsnestserver.exception.handler;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class EventsNestExceptionHandler {

    @ExceptionHandler(EventsNestException.class)
    public ResponseEntity<?> handleEventsNestException(EventsNestException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<?> handleAuthException(RuntimeException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setSuccess(false);
        response.setMessage("validation failed");
        response.setErrors(errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        return errorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<?> errorResponse(String message, HttpStatus status) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(message);
        response.setSuccess(false);
        response.setErrors(List.of(message));
        return new ResponseEntity<>(response, status);
    }
}
