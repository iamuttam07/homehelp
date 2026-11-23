package com.uel.homehelp.provider;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link DayOfWeek} as its ISO number: 1 = Monday ... 7 = Sunday. */
@Converter
class IsoDayOfWeekConverter implements AttributeConverter<DayOfWeek, Integer> {

	@Override
	public Integer convertToDatabaseColumn(DayOfWeek day) {
		return (day != null) ? day.getValue() : null;
	}

	@Override
	public DayOfWeek convertToEntityAttribute(Integer value) {
		return (value != null) ? DayOfWeek.of(value) : null;
	}

}
