package com.example.springbootbasiclogin.util;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public final class ZonedDateTimeConverterUtil {

    private ZonedDateTimeConverterUtil() {}

    @WritingConverter
    public static class ZonedDateTimeToOffsetDateTimeConverter implements Converter<ZonedDateTime, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(ZonedDateTime source) {
            return source != null ? source.toOffsetDateTime() : null;
        }
    }

    @ReadingConverter
    public static class OffsetDateTimeToZonedDateTimeConverter implements Converter<OffsetDateTime, ZonedDateTime> {
        private final ZoneId zoneId;

        public OffsetDateTimeToZonedDateTimeConverter(ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        public ZonedDateTime convert(OffsetDateTime source) {
            if (source == null) return null;
            return zoneId != null ? source.atZoneSameInstant(zoneId) : source.toZonedDateTime();
        }
    }

    @WritingConverter
    public static class ZonedDateTimeToLocalDateTimeConverter implements Converter<ZonedDateTime, LocalDateTime> {
        @Override
        public LocalDateTime convert(ZonedDateTime source) {
            return source != null ? source.toLocalDateTime() : null;
        }
    }

    @ReadingConverter
    public static class LocalDateTimeToZonedDateTimeConverter implements Converter<LocalDateTime, ZonedDateTime> {
        private final ZoneId zoneId;

        public LocalDateTimeToZonedDateTimeConverter(ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        public ZonedDateTime convert(LocalDateTime source) {
            if (source == null) return null;
            return source.atZone(zoneId != null ? zoneId : ZoneId.systemDefault());
        }
    }

    public static List<Object> getConvertersToRegister(ZoneId zoneId) {
        return List.of(
                new ZonedDateTimeToOffsetDateTimeConverter(),
                new OffsetDateTimeToZonedDateTimeConverter(zoneId),
                new ZonedDateTimeToLocalDateTimeConverter(),
                new LocalDateTimeToZonedDateTimeConverter(zoneId)
        );
    }

    public static List<Object> getConvertersToRegister() {
        return getConvertersToRegister(ZoneId.of("Asia/Singapore"));
    }
}
