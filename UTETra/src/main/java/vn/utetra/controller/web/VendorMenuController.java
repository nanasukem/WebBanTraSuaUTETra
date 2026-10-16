package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.utetra.service.AdminProductService;
import vn.utetra.service.VendorBranchService;

@Controller
@RequestMapping("/vendor")
public class VendorMenuController {

	private final AdminProductService adminProductService;
	private final VendorBranchService vendorBranchService;

	public VendorMenuController(AdminProductService adminProductService, VendorBranchService vendorBranchService) {
		this.adminProductService = adminProductService;
		this.vendorBranchService = vendorBranchService;
	}

	@GetMapping("/menu")
	public String vendorMenu(HttpSession session, Model model) {
		Integer branchId = 1; // Mặc định chi nhánh sở hữu của Vendor

		model.addAttribute("products", adminProductService.getAllProducts());
		model.addAttribute("unavailableIds", vendorBranchService.getUnavailableProductIds(branchId));
		return "vendor/menu";
	}

	@PostMapping("/menu/toggle")
	public String toggleProductStatus(@RequestParam Integer productId, @RequestParam boolean isAvailable) {
		Integer branchId = 1;
		vendorBranchService.toggleProductAvailability(branchId, productId, isAvailable);
		return "redirect:/vendor/menu";
	}
}