package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ShipperService;

import java.security.Principal;

@Controller
@RequestMapping("/shipper")
public class ShipperOrderController {

	private final ShipperService shipperService;
	private final CurrentUserService currentUserService;

	public ShipperOrderController(ShipperService shipperService, CurrentUserService currentUserService) {
		this.shipperService = shipperService;
		this.currentUserService = currentUserService;
	}

	@GetMapping({ "", "/", "/orders" })
	public String orders(@RequestParam(defaultValue = "READY") String tab, Principal principal, HttpSession session,
			Model model) {
		User shipper = currentUserService.requireUser(principal, session);
		ShipperService.Board board = shipperService.board(shipper, tab);

		model.addAttribute("shipper", shipper);
		model.addAttribute("branch", shipper.getBranch());
		model.addAttribute("currentTab", board.tab());
		model.addAttribute("readyCount", board.readyCount());
		model.addAttribute("shippingCount", board.shippingCount());
		model.addAttribute("deliveredCount", board.deliveredCount());
		model.addAttribute("doneToday", board.doneToday());
		model.addAttribute("doneMonth", board.doneMonth());
		model.addAttribute("doneTotal", board.doneTotal());
		model.addAttribute("orders", board.orders());
		return "shipper/orders";
	}

	@GetMapping("/stats")
	public String stats(@RequestParam(required = false) String period, @RequestParam(required = false) String month,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
			Principal principal, HttpSession session, Model model) {
		User shipper = currentUserService.requireUser(principal, session);
		vn.utetra.dto.DateRange range = vn.utetra.dto.DateRange.resolve(period, month, year, from, to, "TODAY");
		ShipperService.Stats st = shipperService.stats(shipper, range);

		model.addAttribute("shipper", shipper);
		model.addAttribute("branch", shipper.getBranch());
		model.addAttribute("period", range.period());
		model.addAttribute("range", range);
		model.addAttribute("years", vn.utetra.dto.DateRange.recentYears(5));
		model.addAttribute("orders", st.orders());
		model.addAttribute("countToday", st.countToday());
		model.addAttribute("countMonth", st.countMonth());
		model.addAttribute("countYear", st.countYear());
		model.addAttribute("countAll", st.countAll());
		model.addAttribute("codTotal", st.codTotal());
		model.addAttribute("shipTotal", st.shipTotal());
		return "shipper/stats";
	}

	@PostMapping("/orders/{id}/status")
	public String updateStatus(@PathVariable Integer id, @RequestParam OrderStatus status,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		User shipper = currentUserService.requireUser(principal, session);
		try {
			shipperService.updateStatus(shipper, id, status);
			ra.addFlashAttribute("successMessage", OrderStatus.actionText(status, id, null));
			String nextTab = status == OrderStatus.DELIVERED ? "DELIVERED" : "SHIPPING";
			return "redirect:/shipper/orders?tab=" + nextTab;
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/shipper/orders";
		}
	}
}
