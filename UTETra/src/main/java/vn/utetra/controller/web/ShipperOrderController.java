package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderFlowService;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;

/**
 * Giao diện dành cho Shipper nội bộ của quán (Shipper UTETra):
 * - Xem các đơn Giao tận nơi (DELIVERY) đã pha xong chờ lấy (READY), đang giao (PICKED_UP, DELIVERING) và đã giao (DELIVERED).
 * - Cập nhật trạng thái giao hàng: READY -> PICKED_UP -> DELIVERING -> DELIVERED.
 */
@Controller
@RequestMapping("/shipper")
@Transactional(readOnly = true)
public class ShipperOrderController {

	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;
	private final CurrentUserService currentUserService;

	public ShipperOrderController(OrderRepository orderRepository, OrderFlowService orderFlowService,
			CurrentUserService currentUserService) {
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
		this.currentUserService = currentUserService;
	}

	@GetMapping({ "", "/", "/orders" })
	public String orders(@RequestParam(defaultValue = "READY") String tab, Principal principal, HttpSession session,
			Model model) {
		User shipper = currentUserService.requireUser(principal, session);
		String currentTab = tab == null ? "READY" : tab.trim().toUpperCase();
		if (!List.of("READY", "SHIPPING", "DELIVERED").contains(currentTab)) {
			currentTab = "READY";
		}

		List<Order> allDelivery = orderRepository.findAll().stream()
				.filter(o -> o.getDeliveryType() == DeliveryType.DELIVERY)
				.toList();

		List<Order> readyOrders = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.READY)
				.sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		List<Order> shippingOrders = allDelivery.stream()
				.filter(o -> (o.getStatus() == OrderStatus.PICKED_UP || o.getStatus() == OrderStatus.DELIVERING)
						&& (o.getShipper() == null || o.getShipper().getId().equals(shipper.getId())))
				.sorted(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Order> deliveredOrders = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.DELIVERED
						&& (o.getShipper() == null || o.getShipper().getId().equals(shipper.getId())))
				.sorted(Comparator.comparing(Order::getDeliveredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Order> displayOrders = switch (currentTab) {
			case "SHIPPING" -> shippingOrders;
			case "DELIVERED" -> deliveredOrders;
			default -> readyOrders;
		};

		model.addAttribute("shipper", shipper);
		model.addAttribute("currentTab", currentTab);
		model.addAttribute("readyCount", readyOrders.size());
		model.addAttribute("shippingCount", shippingOrders.size());
		model.addAttribute("deliveredCount", deliveredOrders.size());
		model.addAttribute("orders", displayOrders);
		return "shipper/orders";
	}

	@PostMapping("/orders/{id}/status")
	@Transactional
	public String updateStatus(@PathVariable Integer id, @RequestParam OrderStatus status,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		User shipper = currentUserService.requireUser(principal, session);
		try {
			Order order = orderRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
			if (order.getDeliveryType() != DeliveryType.DELIVERY) {
				throw new BusinessException("Đơn #" + id + " là đơn khách tự đến lấy tại quán");
			}
			if (status != OrderStatus.PICKED_UP && status != OrderStatus.DELIVERING && status != OrderStatus.DELIVERED) {
				throw new BusinessException("Trạng thái cập nhật của Shipper không hợp lệ");
			}
			if (order.getShipper() == null) {
				order.setShipper(shipper);
				orderRepository.saveAndFlush(order);
			}
			orderFlowService.updateStatus(id, status, null);
			ra.addFlashAttribute("successMessage", "Đã cập nhật trạng thái đơn #" + id + " thành công.");
			String nextTab = status == OrderStatus.DELIVERED ? "DELIVERED" : "SHIPPING";
			return "redirect:/shipper/orders?tab=" + nextTab;
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/shipper/orders";
		}
	}
}

