package com.acme.salary.common.time;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter(autoApply = true)
public class IsoLocalDateConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String isoDate) {
        return isoDate == null ? null : LocalDate.parse(isoDate);
    }
}
