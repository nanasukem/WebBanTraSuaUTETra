package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.utetra.service.AdminProductService;
import vn.utetra.service.ManagerBranchService;

@Controller
@RequestMapping("/manager")
public class ManagerMenuController {

	private final AdminProductService adminProductService;
	private final ManagerBranchService managerBranchService;

	public ManagerMenuController(AdminProductService adminProductService, ManagerBranchService managerBranchService) {
		this.adminProductService = adminProductService;
		this.managerBranchService = managerBranchService;
	}

	@GetMapping("/menu")
	public String managerMenu(HttpSession session, Model model) {
		Integer branchId = 1; // Mặc định chi nhánh sở hữu của Manager

		model.addAttribute("products", adminProductService.getAllProducts());
		model.addAttribute("unavailableIds", managerBranchService.getUnavailableProductIds(branchId));
		return "manager/menu";
	}

	@PostMapping("/menu/toggle")
	public String toggleProductStatus(@RequestParam Integer productId, @RequestParam boolean isAvailable) {
		Integer branchId = 1;
		managerBranchService.toggleProductAvailability(branchId, productId, isAvailable);
		return "redirect:/manager/menu";
	}
}