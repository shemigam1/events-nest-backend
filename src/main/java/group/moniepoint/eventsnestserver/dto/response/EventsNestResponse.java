package group.moniepoint.eventsnestserver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class EventsNestResponse<T> {
    private String message;
    private T data;
    private boolean success;
    private List<String> errors;
}
