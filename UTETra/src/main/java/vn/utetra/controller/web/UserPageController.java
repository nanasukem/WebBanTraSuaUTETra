package vn.utetra.controller.web;

import java.security.Principal;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.AddressService;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.FavoriteService;
import vn.utetra.service.UserService;
import vn.utetra.service.ViewedProductService;

@Controller
@RequestMapping("/user")
public class UserPageController {

	private final CurrentUserService currentUserService;
	private final UserService userService;
	private final AddressService addressService;
	private final FavoriteService favoriteService;
	private final ViewedProductService viewedProductService;
	private final vn.utetra.service.MemberService memberService;

	public UserPageController(CurrentUserService currentUserService, UserService userService,
			AddressService addressService, FavoriteService favoriteService, ViewedProductService viewedProductService,
			vn.utetra.service.MemberService memberService) {
		this.memberService = memberService;
		this.currentUserService = currentUserService;
		this.userService = userService;
		this.addressService = addressService;
		this.favoriteService = favoriteService;
		this.viewedProductService = viewedProductService;
	}

	@GetMapping("/profile")
	public String profile(@RequestParam(name = "tab", defaultValue = "info") String tab, Principal principal,
			HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		model.addAttribute("me", userService.getProfile(userId));
		model.addAttribute("addresses", addressService.list(userId));
		model.addAttribute("favoriteCount", favoriteService.count(userId));
		model.addAttribute("viewedCount", viewedProductService.count(userId));
		model.addAttribute("tier", memberService.getTierInfo(userId));
		model.addAttribute("tiers", memberService.allTiers());
		model.addAttribute("tab", List.of("info", "password", "address").contains(tab) ? tab : "info");
		return "user/profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@RequestParam(name = "fullName", required = false) String fullName,
			@RequestParam(name = "phone", required = false) String phone,
			@RequestParam(name = "avatar", required = false) MultipartFile avatar, Principal principal,
			HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			User saved = userService.updateProfile(userId, fullName, phone, avatar);
			session.setAttribute("user", saved);
			ra.addFlashAttribute("success", "Đã cập nhật thông tin cá nhân");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/user/profile?tab=info";
	}

	@PostMapping("/password")
	public String changePassword(@RequestParam(name = "currentPassword", required = false) String currentPassword,
			@RequestParam(name = "newPassword", required = false) String newPassword,
			@RequestParam(name = "confirmPassword", required = false) String confirmPassword, Principal principal,
			HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			userService.changePassword(userId, currentPassword, newPassword, confirmPassword);
			session.setAttribute("user", userService.getProfile(userId));
			ra.addFlashAttribute("success", "Đã đổi mật khẩu. Lần đăng nhập sau dùng mật khẩu mới");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/user/profile?tab=password";
	}

	@GetMapping("/favorites")
	public String favorites(Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		model.addAttribute("products", favoriteService.list(userId));
		model.addAttribute("cardMode", "buy");
		model.addAttribute("isBuyer", true);
		return "user/favorites";
	}

	@GetMapping("/viewed")
	public String viewed(Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		model.addAttribute("products", viewedProductService.recent(userId, ViewedProductService.MAX_ITEMS));
		model.addAttribute("cardMode", "buy");
		model.addAttribute("isBuyer", true);
		return "user/viewed";
	}

	@PostMapping("/viewed/clear")
	public String clearViewed(Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		int n = viewedProductService.clear(userId);
		ra.addFlashAttribute("success", "Đã xoá " + n + " món khỏi lịch sử xem");
		return "redirect:/user/viewed";
	}
}
