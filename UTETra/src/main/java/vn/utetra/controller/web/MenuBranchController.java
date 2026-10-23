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

/** Chọn chi nhánh để xem menu / đặt món (ô "Đang đặt tại" ở đầu menu, nút trên trang chi nhánh) */
@Controller
public class MenuBranchController {

	private final MenuBranchService menuBranchService;

	public MenuBranchController(MenuBranchService menuBranchService) {
		this.menuBranchService = menuBranchService;
	}

	/** Form thường: chọn xong quay lại trang đang xem (back) hoặc trang trước */
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

	/** Trang thanh toán đổi chi nhánh -> menu cũng đổi theo (gọi bằng fetch) */
	@PostMapping("/menu/branch/ajax")
	@ResponseBody
	public ResponseEntity<Void> chooseAjax(@RequestParam(required = false) Integer branchId, HttpSession session) {
		menuBranchService.choose(session, branchId);
		return ResponseEntity.noContent().build();
	}

	/** Trình duyệt gửi vị trí -> tự chọn chi nhánh gần nhất (chỉ khi khách chưa chọn / chưa từ chối) */
	@PostMapping("/menu/branch/nearest")
	@ResponseBody
	public java.util.Map<String, Object> nearest(@RequestParam double lat, @RequestParam double lng, HttpSession session) {
		Branch b = menuBranchService.chooseNearest(session, lat, lng);
		return b == null ? java.util.Map.of("chosen", false) : java.util.Map.of("chosen", true, "branchId", b.getId());
	}

	/** Dữ liệu dùng chung cho các trang có menu: chi nhánh đang xem + món / topping hết ở đó */
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
			// Chưa chọn và chưa bấm "Chọn khi thanh toán" -> trang hỏi vị trí để tự chọn chi nhánh gần nhất
			model.addAttribute("menuBranchAsk", b == null && !menuBranchService.locked(session)
					&& !Boolean.TRUE.equals(session.getAttribute(MenuBranchService.SESSION_NONE)));
			model.addAttribute("menuBranches", menuBranchService.openBranches());
			model.addAttribute("menuOutIds", b == null ? java.util.Set.of() : menuBranchService.outProductIds(b.getId()));
		}
	}
}
