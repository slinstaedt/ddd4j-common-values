package org.ddd4j.schema.avro.conversion;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Period;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.generic.GenericData;

public class Java8TimeConversions {

	public static final Conversion<ZoneId> ZONE_ID = StringConversion.ofString(ZoneId.class, ZoneId::getId, ZoneId::of);
	public static final Conversion<ZoneOffset> ZONE_OFFSET = IntConversion.of(ZoneOffset.class, ZoneOffset::getTotalSeconds,
			ZoneOffset::ofTotalSeconds);

	public static final Conversion<LocalDate> LOCAL_DATE = LongConversion.of(LocalDate.class, LocalDate::toEpochDay, LocalDate::ofEpochDay);
	public static final Conversion<LocalTime> LOCAL_TIME = LongConversion.of(LocalTime.class, LocalTime::toNanoOfDay,
			LocalTime::ofNanoOfDay);
	public static final Conversion<LocalDateTime> LOCAL_DATE_TIME = RecordConversion.builder(LocalDateTime.class)
			.with("date", LOCAL_DATE, LocalDateTime::toLocalDate, (n, d) -> d)
			.with("time", LOCAL_TIME, LocalDateTime::toLocalTime, (d, t) -> LocalDateTime.of(d, t))
			.build(Function.identity());

	public static final Conversion<Duration> DURATION_EXACT = LongConversion.of(Duration.class, Duration::toNanos, Duration::ofNanos);
	public static final Conversion<Duration> DURATION = LongConversion.of(Duration.class, Duration::toMillis, Duration::ofMillis);
	public static final Conversion<Period> PERIOD = StringConversion.of(Period.class, Period::toString, Period::parse);

	public static final Conversion<Instant> INSTANT = LongConversion.of(Instant.class, Instant::toEpochMilli, Instant::ofEpochMilli);
	public static final Conversion<DayOfWeek> DAY_OF_WEEK = EnumConversion.of(DayOfWeek.class);
	public static final Conversion<Month> MONTH = EnumConversion.of(Month.class);
	public static final Conversion<Year> YEAR = IntConversion.of(Year.class, Year::getValue, Year::of);
	// public static final Conversion<MonthDay> MONTH_DAY = RecordConversion.builder(MonthDay.class)
	// .with("month", MONTH, MonthDay::getMonth, (n, m) -> m)
	// .with("day", null/* TODO */, MonthDay::getDayOfMonth, (m, d) -> MonthDay.of(m, d))
	// .build(Function.identity());
	//
	// public static final Conversion<ZonedDateTime> ZONED_DATE_TIME = RecordConversion.builder(ZonedDateTime.class)
	// .with("id", ZONE_ID, ZonedDateTime::getZone, (n, i) -> i)
	// .with("offset", ZONE_OFFSET, ZonedDateTime::getOffset, (i, o) -> null)
	// .with("local", LOCAL_DATE_TIME, ZonedDateTime::toLocalDateTime, null)
	// .build(null);

	public static <T extends GenericData> T registerAll(T data) {
		data.addLogicalTypeConversion(ZONE_ID);
		data.addLogicalTypeConversion(ZONE_OFFSET);
		data.addLogicalTypeConversion(LOCAL_DATE);
		data.addLogicalTypeConversion(LOCAL_TIME);
		data.addLogicalTypeConversion(LOCAL_DATE_TIME);
		data.addLogicalTypeConversion(DURATION);
		data.addLogicalTypeConversion(PERIOD);
		data.addLogicalTypeConversion(INSTANT);
		data.addLogicalTypeConversion(YEAR);
		return data;
	}

	private Java8TimeConversions() {
	}
}