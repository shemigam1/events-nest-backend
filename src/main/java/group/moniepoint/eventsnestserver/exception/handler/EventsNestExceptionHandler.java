package group.moniepoint.eventsnestserver.exception.handler;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.exception.auth.EmailAlreadyInUseException;
import group.moniepoint.eventsnestserver.exception.auth.InvitationTokenInvalidException;
import group.moniepoint.eventsnestserver.exception.event.EventNotPublishedException;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.MissingRequestBodyException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class EventsNestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFound(ResourceNotFoundException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EventNotPublishedException.class)
    public ResponseEntity<?> handleEventNotPublished(EventNotPublishedException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<?> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvitationTokenInvalidException.class)
    public ResponseEntity<?> handleInvitationTokenInvalid(InvitationTokenInvalidException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.GONE);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorized(UnauthorizedException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidEventStateException.class)
    public ResponseEntity<?> handleInvalidEventState(InvalidEventStateException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EventsNestException.class)
    public ResponseEntity<?> handleEventsNestException(EventsNestException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<?> handleAuthException(RuntimeException ex) {
        return errorResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return errorResponse("access denied", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return errorResponse("seats are no longer available, please try again", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMissingRequestBody(HttpMessageNotReadableException ex) {
        return handleEventsNestException(new MissingRequestBodyException());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return errorResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = new java.util.ArrayList<>();
        ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .forEach(errors::add);
        ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> ge.getDefaultMessage())
                .forEach(errors::add);

        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setSuccess(false);
        response.setMessage("validation failed");
        response.setErrors(errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return errorResponse("an unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<?> errorResponse(String message, HttpStatus status) {
        EventsNestResponse<?> response = new EventsNestResponse<>();
        response.setMessage(message);
        response.setSuccess(false);
        response.setErrors(List.of(message));
        return new ResponseEntity<>(response, status);
    }
}
