package vn.utetra.controller.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.ReportService;

@Controller
@RequestMapping("/admin/reports")
@Transactional(readOnly = true)
public class AdminReportController {

	private static final int MONTHS = 12;
	private static final int TOP = 10;

	private final OrderRepository orderRepository;
	private final BranchRepository branchRepository;
	private final ReportService reportService;

	public AdminReportController(OrderRepository orderRepository, BranchRepository branchRepository,
			ReportService reportService) {
		this.orderRepository = orderRepository;
		this.branchRepository = branchRepository;
		this.reportService = reportService;
	}

	@GetMapping
	public String index(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) Integer branchId,
			Model model) {

		LocalDate today = LocalDate.now();
		LocalDate firstDayOfMonth = today.withDayOfMonth(1);
		if (startDate == null) startDate = firstDayOfMonth;
		if (endDate == null) endDate = today;
		if (startDate.isAfter(endDate)) {
			LocalDate t = startDate;
			startDate = endDate;
			endDate = t;
		}

		OrderStatus delivered = OrderStatus.DELIVERED;
		LocalDateTime todayStart = today.atStartOfDay();
		LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
		LocalDateTime monthStart = firstDayOfMonth.atStartOfDay();
		LocalDateTime nextMonthStart = firstDayOfMonth.plusMonths(1).atStartOfDay();

		model.addAttribute("todayRevenue", orderRepository.sumRevenueOfBranch(delivered, todayStart, tomorrowStart, branchId));
		model.addAttribute("todayOrders", orderRepository.countDeliveredOfBranch(delivered, todayStart, tomorrowStart, branchId));
		model.addAttribute("monthRevenue", orderRepository.sumRevenueOfBranch(delivered, monthStart, nextMonthStart, branchId));
		model.addAttribute("monthOrders", orderRepository.countDeliveredOfBranch(delivered, monthStart, nextMonthStart, branchId));

		List<ReportService.Point> days = reportService.byDay(startDate, endDate, branchId);
		ReportService.Point sum = ReportService.total(days);
		model.addAttribute("selectedRevenue", sum.revenue());
		model.addAttribute("selectedOrders", sum.orders());
		model.addAttribute("selectedAverage", sum.average());
		model.addAttribute("chartLabels", days.stream().map(ReportService.Point::label).toList());
		model.addAttribute("chartValues", days.stream().map(ReportService.Point::revenue).toList());

		List<ReportService.Point> months = reportService.byMonth(MONTHS, endDate, branchId);
		model.addAttribute("months", months);
		model.addAttribute("monthLabels", months.stream().map(ReportService.Point::label).toList());
		model.addAttribute("monthValues", months.stream().map(ReportService.Point::revenue).toList());
		model.addAttribute("monthCounts", months.stream().map(ReportService.Point::orders).toList());

		model.addAttribute("topProducts", reportService.topProducts(startDate, endDate, branchId, TOP));

		final Integer bid = branchId;
		model.addAttribute("branchReports", reportService.byBranch(startDate, endDate).stream()
				.filter(b -> bid == null || b.branchId().equals(bid)).toList());

		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("branches", branchRepository.findAll().stream()
				.sorted(Comparator.comparing(Branch::getId)).toList());
		model.addAttribute("selectedBranchId", branchId);
		model.addAttribute("selectedBranch", branchId == null ? null : branchRepository.findById(branchId).orElse(null));
		return "admin/reports/index";
	}
}
