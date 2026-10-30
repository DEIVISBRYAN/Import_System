package com.ejemplo.crudpersona.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Converter(autoApply = true)
public class LocalDateTimeTextConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(DB_FORMAT);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String value = dbData.trim();
        if (value.matches("\\d+")) {
            long numeric = Long.parseLong(value);
            Instant instant = value.length() > 10 ? Instant.ofEpochMilli(numeric) : Instant.ofEpochSecond(numeric);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        value = value.replace('T', ' ');
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay();
        }
        if (value.length() > 23) {
            value = value.substring(0, 23);
        }
        if (value.length() == 19) {
            value = value + ".000";
        }
        return LocalDateTime.parse(value, DB_FORMAT);
    }
}
