package com.ejemplo.crudpersona.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Converter(autoApply = true)
public class LocalDateTextConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.format(DB_FORMAT);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String value = dbData.trim();
        if (value.matches("\\d+")) {
            long numeric = Long.parseLong(value);
            Instant instant = value.length() > 10 ? Instant.ofEpochMilli(numeric) : Instant.ofEpochSecond(numeric);
            return LocalDate.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value.length() > 10) {
            value = value.substring(0, 10);
        }
        return LocalDate.parse(value, DB_FORMAT);
    }
}
