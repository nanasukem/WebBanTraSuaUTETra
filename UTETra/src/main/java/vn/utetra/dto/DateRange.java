package vn.utetra.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public record DateRange(String period, LocalDate from, LocalDate to, String label, String query, String month,
		Integer year) {

	private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

	public LocalDateTime start() {
		return (from == null ? EARLIEST : from).atStartOfDay();
	}

	public LocalDateTime end() {
		return (to == null ? LocalDate.now().plusYears(1) : to).plusDays(1).atStartOfDay();
	}

	public boolean contains(LocalDateTime t) {
		return t != null && !t.isBefore(start()) && t.isBefore(end());
	}

	public static List<Integer> recentYears(int count) {
		List<Integer> list = new ArrayList<>();
		int y = LocalDate.now().getYear();
		for (int i = 0; i < count; i++) list.add(y - i);
		return list;
	}

	public static DateRange resolve(String period, String month, Integer year, LocalDate from, LocalDate to,
			String defaultPeriod) {
		LocalDate today = LocalDate.now();
		if (from != null || to != null) {
			LocalDate f = from == null ? to : from;
			LocalDate t = to == null ? from : to;
			if (f.isAfter(t)) {
				LocalDate x = f;
				f = t;
				t = x;
			}
			return new DateRange("CUSTOM", f, t, "Từ " + f.format(D) + " đến " + t.format(D),
					"from=" + f + "&to=" + t, null, null);
		}
		if (month != null && !month.isBlank()) {
			try {
				YearMonth m = YearMonth.parse(month.trim());
				return new DateRange("PICK_MONTH", m.atDay(1), m.atEndOfMonth(),
						String.format("Tháng %02d/%d", m.getMonthValue(), m.getYear()), "month=" + m, m.toString(), null);
			} catch (RuntimeException ignored) {
			}
		}
		if (year != null && year > 1999 && year < 3000) {
			return new DateRange("PICK_YEAR", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "Năm " + year,
					"year=" + year, null, year);
		}
		String p = period == null ? defaultPeriod : period.trim().toUpperCase();
		return switch (p) {
		case "TODAY" -> new DateRange("TODAY", today, today, "Hôm nay " + today.format(D), "period=TODAY", null, null);
		case "YEAR" -> new DateRange("YEAR", today.withDayOfYear(1), today, "Năm " + today.getYear(), "period=YEAR",
				null, null);
		case "ALL" -> new DateRange("ALL", null, null, "Tất cả thời gian", "period=ALL", null, null);
		case "MONTH" -> new DateRange("MONTH", today.withDayOfMonth(1), today,
				String.format("Tháng %02d/%d", today.getMonthValue(), today.getYear()), "period=MONTH", null, null);
		default -> resolve(defaultPeriod, null, null, null, null, defaultPeriod);
		};
	}
}
