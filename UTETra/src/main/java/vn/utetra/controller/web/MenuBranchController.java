package vn.utetra.controller.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import vn.utetra.entity.Branch;
import vn.utetra.service.MenuBranchService;

@Controller
public class MenuBranchController {

	private final MenuBranchService menuBranchService;

	public MenuBranchController(MenuBranchService menuBranchService) {
		this.menuBranchService = menuBranchService;
	}

	@PostMapping("/menu/branch")
	public String choose(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String back, HttpSession session, HttpServletRequest request) {
		menuBranchService.choose(session, branchId);
		String to = back != null && back.startsWith("/") && !back.startsWith("//") ? back : null;
		if (to == null) {
			String ref = request.getHeader("Referer");
			to = ref != null && !ref.isBlank() ? ref : "/menu";
		}
		return "redirect:" + to;
	}

	@PostMapping("/menu/branch/ajax")
	@ResponseBody
	public ResponseEntity<Void> chooseAjax(@RequestParam(required = false) Integer branchId, HttpSession session) {
		menuBranchService.choose(session, branchId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/menu/branch/nearest")
	@ResponseBody
	public java.util.Map<String, Object> nearest(@RequestParam double lat, @RequestParam double lng, HttpSession session) {
		Branch b = menuBranchService.chooseNearest(session, lat, lng);
		return b == null ? java.util.Map.of("chosen", false) : java.util.Map.of("chosen", true, "branchId", b.getId());
	}

	@ControllerAdvice(assignableTypes = { HomeController.class, ProductPageController.class,
			UserPageController.class, BranchDetailController.class, BranchPageController.class })
	public static class Advice {
		private final MenuBranchService menuBranchService;

		public Advice(MenuBranchService menuBranchService) {
			this.menuBranchService = menuBranchService;
		}

		@ModelAttribute
		public void menuBranch(HttpSession session, Model model) {
			Branch b = menuBranchService.current(session);
			model.addAttribute("menuBranch", b);
			model.addAttribute("menuBranchLocked", menuBranchService.locked(session));
			model.addAttribute("menuBranchAuto", menuBranchService.auto(session));
			model.addAttribute("menuBranchAsk", b == null && !menuBranchService.locked(session)
					&& !Boolean.TRUE.equals(session.getAttribute(MenuBranchService.SESSION_NONE)));
			model.addAttribute("menuBranches", menuBranchService.openBranches());
			model.addAttribute("menuOutIds", b == null ? java.util.Set.of() : menuBranchService.outProductIds(b.getId()));
		}
	}
}
