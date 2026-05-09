package group.moniepoint.eventsnestserver.events.dto.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EventEditProposedChangesConverter implements AttributeConverter<EventEditProposedChanges, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String convertToDatabaseColumn(EventEditProposedChanges attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EventEditProposedChanges", e);
        }
    }

    @Override
    public EventEditProposedChanges convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return MAPPER.readValue(dbData, EventEditProposedChanges.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize EventEditProposedChanges", e);
        }
    }
}
