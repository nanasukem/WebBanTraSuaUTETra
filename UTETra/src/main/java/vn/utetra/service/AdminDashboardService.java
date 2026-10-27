package vn.utetra.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final BranchRepository branchRepository;
	private final ReportService reportService;

	public AdminDashboardService(OrderRepository orderRepository, UserRepository userRepository,
			ProductRepository productRepository, BranchRepository branchRepository, ReportService reportService) {
		this.orderRepository = orderRepository;
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.branchRepository = branchRepository;
		this.reportService = reportService;
	}

	public record Overview(long orderCount, long pendingOrders, long deliveredOrders, BigDecimal revenue,
			BigDecimal revenueSales, BigDecimal revenueShipping, long userCount, long activeUsers, long productCount,
			long activeProducts, long branchCount, long approvedBranches, List<ReportService.Point> week,
			ReportService.Point weekSum, List<ReportService.BranchRevenue> branchMonth) {
	}

	public Overview overview() {
		List<Order> real = orderRepository.findAll().stream().filter(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT)
				.toList();
		List<Order> delivered = real.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).toList();
		List<Order> paidOut = real.stream().filter(Order::countsAsRevenue).toList();
		BigDecimal revenue = paidOut.stream().map(Order::getSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal ship = paidOut.stream().map(Order::getShippingFee).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		var users = userRepository.findAll();
		var products = productRepository.findAll();
		var branches = branchRepository.findAll();

		LocalDate today = LocalDate.now();
		List<ReportService.Point> week = reportService.byDay(today.minusDays(6), today, null);

		return new Overview(real.size(), real.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count(),
				delivered.size(), revenue, revenue.subtract(ship), ship, users.size(),
				users.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count(), products.size(),
				products.stream().filter(p -> Boolean.TRUE.equals(p.getIsAvailable())).count(), branches.size(),
				branches.stream().filter(b -> b.getStatus() == BranchStatus.APPROVED).count(), week,
				ReportService.total(week), reportService.byBranch(today.withDayOfMonth(1), today));
	}
}
