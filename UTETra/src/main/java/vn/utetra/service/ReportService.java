package vn.utetra.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.OrderDetailRepository;
import vn.utetra.repository.OrderRepository;

@Service
@Transactional(readOnly = true)
public class ReportService {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

	private final OrderRepository orderRepository;
	private final OrderDetailRepository orderDetailRepository;

	public ReportService(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository) {
		this.orderRepository = orderRepository;
		this.orderDetailRepository = orderDetailRepository;
	}

	public record Point(String label, BigDecimal revenue, BigDecimal shipping, long orders) {
		public BigDecimal sales() {
			return revenue.subtract(shipping);
		}

		public BigDecimal average() {
			return ReportService.average(revenue, orders);
		}
	}

	public record TopProduct(Integer productId, String name, long quantity, BigDecimal amount, long orders) {
	}

	public record BranchRevenue(Integer branchId, String branchName, BigDecimal revenue, BigDecimal shipping,
			long orders) {
		public BigDecimal sales() {
			return revenue.subtract(shipping);
		}

		public BigDecimal average() {
			return ReportService.average(revenue, orders);
		}
	}

	public List<Point> byDay(LocalDate from, LocalDate to, Integer branchId) {
		Map<LocalDate, Point> map = new LinkedHashMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			map.put(d, new Point(d.format(DAY), BigDecimal.ZERO, BigDecimal.ZERO, 0));
		}
		for (Object[] r : orderRepository.revenueByDay(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
				branchId == null ? 0 : branchId)) {
			LocalDate d = toLocalDate(r[0]);
			if (d != null && map.containsKey(d)) {
				map.put(d, new Point(d.format(DAY), money(r[1]), money(r[2]), ((Number) r[3]).longValue()));
			}
		}
		return new ArrayList<>(map.values());
	}

	public List<Point> byMonth(int months, LocalDate until, Integer branchId) {
		YearMonth last = YearMonth.from(until);
		YearMonth first = last.minusMonths(months - 1L);
		Map<YearMonth, Point> map = new LinkedHashMap<>();
		for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
			map.put(m, new Point(label(m), BigDecimal.ZERO, BigDecimal.ZERO, 0));
		}
		for (Object[] r : orderRepository.revenueByMonth(OrderStatus.DELIVERED, first.atDay(1).atStartOfDay(),
				last.plusMonths(1).atDay(1).atStartOfDay(), branchId)) {
			YearMonth m = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
			map.put(m, new Point(label(m), money(r[2]), money(r[3]), ((Number) r[4]).longValue()));
		}
		return new ArrayList<>(map.values());
	}

	public List<TopProduct> topProducts(LocalDate from, LocalDate to, Integer branchId, int limit) {
		List<TopProduct> list = new ArrayList<>();
		for (Object[] r : orderDetailRepository.topProducts(OrderStatus.DELIVERED, from.atStartOfDay(),
				to.plusDays(1).atStartOfDay(), branchId, PageRequest.of(0, limit))) {
			list.add(new TopProduct((Integer) r[0], (String) r[1], ((Number) r[2]).longValue(), money(r[3]),
					((Number) r[4]).longValue()));
		}
		return list;
	}

	public List<BranchRevenue> byBranch(LocalDate from, LocalDate to) {
		List<BranchRevenue> list = new ArrayList<>();
		for (Object[] r : orderRepository.revenueByBranch(OrderStatus.DELIVERED, from.atStartOfDay(),
				to.plusDays(1).atStartOfDay())) {
			list.add(new BranchRevenue((Integer) r[0], (String) r[1], money(r[2]), money(r[3]),
					((Number) r[4]).longValue()));
		}
		return list;
	}

	public Point summary(LocalDateTime start, LocalDateTime end, Integer branchId) {
		List<Object[]> rows = orderRepository.summaryOfBranch(OrderStatus.DELIVERED, start, end, branchId);
		if (rows.isEmpty()) return new Point("Tổng", BigDecimal.ZERO, BigDecimal.ZERO, 0);
		Object[] r = rows.get(0);
		return new Point("Tổng", money(r[0]), money(r[1]), r[2] == null ? 0 : ((Number) r[2]).longValue());
	}

	public org.springframework.data.domain.Page<vn.utetra.entity.Order> shippedBetween(LocalDateTime start,
			LocalDateTime end, Integer branchId, int page, int size) {
		return orderRepository.findShippedBetween(OrderStatus.DELIVERED, start, end, branchId,
				PageRequest.of(Math.max(page, 0), size));
	}

	public List<vn.utetra.entity.Order> latestShipped(Integer branchId, int limit) {
		return orderRepository.findLatestShipped(OrderStatus.DELIVERED, branchId, PageRequest.of(0, limit));
	}

	public static Point total(List<Point> points) {
		BigDecimal sum = BigDecimal.ZERO;
		BigDecimal ship = BigDecimal.ZERO;
		long n = 0;
		for (Point p : points) {
			sum = sum.add(p.revenue());
			ship = ship.add(p.shipping());
			n += p.orders();
		}
		return new Point("Tổng", sum, ship, n);
	}

	public static BigDecimal average(BigDecimal revenue, long orders) {
		return orders <= 0 || revenue == null ? BigDecimal.ZERO
				: revenue.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
	}

	private static String label(YearMonth m) {
		return String.format("%02d/%d", m.getMonthValue(), m.getYear());
	}

	private static BigDecimal money(Object o) {
		if (o == null) return BigDecimal.ZERO;
		if (o instanceof BigDecimal b) return b;
		return new BigDecimal(o.toString());
	}

	private static LocalDate toLocalDate(Object o) {
		if (o == null) return null;
		if (o instanceof LocalDate d) return d;
		if (o instanceof java.sql.Date d) return d.toLocalDate();
		if (o instanceof LocalDateTime t) return t.toLocalDate();
		if (o instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
		return LocalDate.parse(o.toString().substring(0, 10));
	}
}
