package vn.utetra.controller.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.Branch;
import vn.utetra.service.ReportService;

@Controller
@RequestMapping("/admin/reports")
public class AdminReportController {

	private static final int MONTHS = 12;
	private static final int TOP = 10;

	private final vn.utetra.service.BranchService branchService;
	private final ReportService reportService;

	public AdminReportController(vn.utetra.service.BranchService branchService, ReportService reportService) {
		this.branchService = branchService;
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

		LocalDateTime todayStart = today.atStartOfDay();
		LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
		LocalDateTime monthStart = firstDayOfMonth.atStartOfDay();
		LocalDateTime nextMonthStart = firstDayOfMonth.plusMonths(1).atStartOfDay();

		ReportService.Point todayStat = reportService.summary(todayStart, tomorrowStart, branchId);
		ReportService.Point monthStat = reportService.summary(monthStart, nextMonthStart, branchId);
		model.addAttribute("todayStat", todayStat);
		model.addAttribute("monthStat", monthStat);
		model.addAttribute("todayRevenue", todayStat.revenue());
		model.addAttribute("todayOrders", todayStat.orders());
		model.addAttribute("monthRevenue", monthStat.revenue());
		model.addAttribute("monthOrders", monthStat.orders());

		List<ReportService.Point> days = reportService.byDay(startDate, endDate, branchId);
		ReportService.Point sum = ReportService.total(days);
		model.addAttribute("selectedStat", sum);
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
		model.addAttribute("monthSales", months.stream().map(ReportService.Point::sales).toList());
		model.addAttribute("monthShipping", months.stream().map(ReportService.Point::shipping).toList());

		model.addAttribute("topProducts", reportService.topProducts(startDate, endDate, branchId, TOP));

		final Integer bid = branchId;
		model.addAttribute("branchReports", reportService.byBranch(startDate, endDate).stream()
				.filter(b -> bid == null || b.branchId().equals(bid)).toList());

		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("branches", branchService.findAll().stream()
				.sorted(Comparator.comparing(Branch::getId)).toList());
		model.addAttribute("selectedBranchId", branchId);
		model.addAttribute("selectedBranch", branchId == null ? null : branchService.findOptional(branchId).orElse(null));
		return "admin/reports/index";
	}
}
