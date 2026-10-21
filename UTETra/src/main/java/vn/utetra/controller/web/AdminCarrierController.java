package vn.utetra.controller.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.request.CarrierReq;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.CarrierService;

/** Quản lý nhà vận chuyển cho ADMIN */
@Controller
@RequestMapping("/admin/carriers")
public class AdminCarrierController {

	private final CarrierService carrierService;

	public AdminCarrierController(CarrierService carrierService) {
		this.carrierService = carrierService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) String keyword, Model model) {
		model.addAttribute("carriers", carrierService.search(keyword));
		model.addAttribute("keyword", keyword);
		return "admin/carriers/index";
	}

	@GetMapping("/create")
	public String createForm(Model model) {
		model.addAttribute("carrierReq", new CarrierReq());
		model.addAttribute("carrierId", null);
		return "admin/carriers/form";
	}

	@PostMapping("/create")
	public String create(@Valid @ModelAttribute("carrierReq") CarrierReq req, BindingResult result, Model model,
			RedirectAttributes ra) {
		model.addAttribute("carrierId", null);
		if (result.hasErrors())
			return "admin/carriers/form";
		try {
			carrierService.create(req);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return "admin/carriers/form";
		}
		ra.addFlashAttribute("success", "Đã thêm nhà vận chuyển " + req.getName());
		return "redirect:/admin/carriers";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model) {
		model.addAttribute("carrierReq", carrierService.toReq(carrierService.findById(id)));
		model.addAttribute("carrierId", id);
		return "admin/carriers/form";
	}

	@PostMapping("/{id}/edit")
	public String update(@PathVariable Integer id, @Valid @ModelAttribute("carrierReq") CarrierReq req,
			BindingResult result, Model model, RedirectAttributes ra) {
		model.addAttribute("carrierId", id);
		if (result.hasErrors())
			return "admin/carriers/form";
		try {
			carrierService.update(id, req);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return "admin/carriers/form";
		}
		ra.addFlashAttribute("success", "Đã cập nhật nhà vận chuyển " + req.getName());
		return "redirect:/admin/carriers";
	}

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Integer id, RedirectAttributes ra) {
		carrierService.toggleActive(id);
		ra.addFlashAttribute("success", "Đã đổi trạng thái nhà vận chuyển #" + id);
		return "redirect:/admin/carriers";
	}
}