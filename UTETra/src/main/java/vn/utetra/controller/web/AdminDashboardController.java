package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;



@Controller
public class AdminDashboardController {

	private final vn.utetra.service.AdminDashboardService dashboardService;
	private final vn.utetra.service.AdminOrderService adminOrderService;

	public AdminDashboardController(vn.utetra.service.AdminDashboardService dashboardService,
			vn.utetra.service.AdminOrderService adminOrderService) {
		this.dashboardService = dashboardService;
		this.adminOrderService = adminOrderService;
	}

	@GetMapping("/admin/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("pageTitle", "Tổng quan quản trị");

		vn.utetra.service.AdminDashboardService.Overview o = dashboardService.overview();
		model.addAttribute("orderCount", o.orderCount());
		model.addAttribute("pendingOrders", o.pendingOrders());
		model.addAttribute("deliveredOrders", o.deliveredOrders());
		model.addAttribute("revenue", o.revenue());
		model.addAttribute("revenueShipping", o.revenueShipping());
		model.addAttribute("revenueSales", o.revenueSales());
		model.addAttribute("userCount", o.userCount());
		model.addAttribute("activeUsers", o.activeUsers());
		model.addAttribute("productCount", o.productCount());
		model.addAttribute("activeProducts", o.activeProducts());
		model.addAttribute("branchCount", o.branchCount());
		model.addAttribute("approvedBranches", o.approvedBranches());

		model.addAttribute("weekLabels", o.week().stream().map(vn.utetra.service.ReportService.Point::label).toList());
		model.addAttribute("weekValues", o.week().stream().map(vn.utetra.service.ReportService.Point::revenue).toList());
		model.addAttribute("weekRevenue", o.weekSum().revenue());
		model.addAttribute("weekOrders", o.weekSum().orders());
		model.addAttribute("branchLabels",
				o.branchMonth().stream().map(vn.utetra.service.ReportService.BranchRevenue::branchName).toList());
		model.addAttribute("branchValues",
				o.branchMonth().stream().map(vn.utetra.service.ReportService.BranchRevenue::revenue).toList());

		vn.utetra.service.AdminOrderService.IssueMarks marks = adminOrderService.issueMarks();
		model.addAttribute("issueLabels", marks.labels());
		model.addAttribute("issuePending", marks.pending());

		model.addAttribute("recentOrders", adminOrderService.recent(8));
		return "admin/dashboard";
	}
}
