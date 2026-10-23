package vn.utetra.controller.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.Product;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tổng quan hệ thống của Admin: số đơn, người dùng, sản phẩm, chi nhánh, doanh thu, 8 đơn mới nhất.
 * Doanh thu = tiền món sau giảm giá của các đơn đã giao (tổng tiền đơn gồm cả phí ship Shipper UTETra, trừ tiền đã hoàn).
 */
@Controller
@Transactional(readOnly = true)
public class AdminDashboardController {

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final BranchRepository branchRepository;

	public AdminDashboardController(OrderRepository orderRepository, UserRepository userRepository,
			ProductRepository productRepository, BranchRepository branchRepository) {
		this.orderRepository = orderRepository;
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.branchRepository = branchRepository;
	}

	@GetMapping("/admin/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("pageTitle", "Tổng quan quản trị");

		List<Order> orders = orderRepository.findAll();
		// Đơn chờ thanh toán (VNPAY/MoMo chưa trả xong) chưa phải đơn thật -> không đếm
		List<Order> real = orders.stream().filter(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT).toList();
		List<Order> delivered = real.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).toList();

		model.addAttribute("orderCount", real.size());
		model.addAttribute("pendingOrders", real.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count());
		model.addAttribute("deliveredOrders", delivered.size());
		model.addAttribute("revenue",
				delivered.stream().map(Order::getSalesAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

		List<User> users = userRepository.findAll();
		model.addAttribute("userCount", users.size());
		model.addAttribute("activeUsers", users.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count());

		List<Product> products = productRepository.findAll();
		model.addAttribute("productCount", products.size());
		model.addAttribute("activeProducts",
				products.stream().filter(p -> Boolean.TRUE.equals(p.getIsAvailable())).count());

		List<Branch> branches = branchRepository.findAll();
		model.addAttribute("branchCount", branches.size());
		model.addAttribute("approvedBranches",
				branches.stream().filter(b -> b.getStatus() == BranchStatus.APPROVED).count());

		model.addAttribute("recentOrders", orderRepository
				.findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
		return "admin/dashboard";
	}
}
