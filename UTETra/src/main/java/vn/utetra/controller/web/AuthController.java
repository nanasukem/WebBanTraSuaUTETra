package vn.utetra.controller.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.exception.AccountNotVerifiedException;
import vn.utetra.service.AuthenticationService;
import vn.utetra.service.JwtService;

@Controller
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticationService authenticationService;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public AuthController(AuthenticationService authenticationService, JwtService jwtService,
			UserDetailsService userDetailsService) {
		this.authenticationService = authenticationService;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@GetMapping("/login")
	public String loginPage(Model model) {
		model.addAttribute("loginRequest", new LoginRequest());
		return "auth/login";
	}

	@PostMapping("/login")
	public String handleLogin(@ModelAttribute("loginRequest") LoginRequest loginRequest, HttpServletResponse response,
			HttpSession session, Model model) {
		try {
			User authenticatedUser = authenticationService.authenticate(loginRequest);

			UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getUsername());
			String token = jwtService.generateToken(userDetails);

			Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
			jwtCookie.setHttpOnly(true);
			jwtCookie.setPath("/");
			jwtCookie.setMaxAge((int) (jwtService.getExpirationTime() / 1000));
			response.addCookie(jwtCookie);

			session.setAttribute("currentUser", authenticatedUser);

			if ("ADMIN".equalsIgnoreCase(authenticatedUser.getRole().getName())) {
				return "redirect:/admin/dashboard";
			} else if ("MANAGER".equalsIgnoreCase(authenticatedUser.getRole().getName())) {
				return "redirect:/manager/dashboard";
			}

			return "redirect:/menu";
		} catch (AccountNotVerifiedException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("unverifiedEmail", e.getEmail());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/login";
		}
	}

	@PostMapping("/resend-otp")
	public String handleResendOtp(@RequestParam("email") String email, RedirectAttributes redirectAttributes) {
		try {
			boolean sent = authenticationService.resendRegisterOtp(email);
			redirectAttributes.addFlashAttribute("success", sent ? "Đã gửi mã OTP mới tới email của bạn!"
					: "Mã OTP vừa được gửi, vui lòng kiểm tra email (kể cả mục Spam).");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		redirectAttributes.addAttribute("email", email);
		return "redirect:/auth/verify-otp";
	}

	@GetMapping("/register")
	public String registerPage(Model model) {
		model.addAttribute("registerRequest", new RegisterRequest());
		return "auth/register";
	}

	@PostMapping("/register")
	public String handleRegister(@ModelAttribute("registerRequest") RegisterRequest registerRequest, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			User user = authenticationService.signup(registerRequest);
			redirectAttributes.addAttribute("email", user.getEmail());
			return "redirect:/auth/verify-otp";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/register";
		}
	}

	@GetMapping("/verify-otp")
	public String verifyOtpPage(@RequestParam("email") String email, Model model) {
		model.addAttribute("email", email);
		return "auth/verify-otp";
	}

	@PostMapping("/verify-otp")
	public String handleVerifyOtp(@RequestParam("email") String email, @RequestParam("code") String code, Model model) {
		try {
			authenticationService.verifyOtp(email, code);
			model.addAttribute("success", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập.");
			model.addAttribute("loginRequest", new LoginRequest());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("email", email);
			model.addAttribute("error", e.getMessage());
			return "auth/verify-otp";
		}
	}

	@GetMapping("/forgot-password")
	public String forgotPasswordPage() {
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String handleForgotPassword(@RequestParam("email") String email, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			authenticationService.processForgotPassword(email);
			redirectAttributes.addAttribute("email", email);
			return "redirect:/auth/reset-password";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/forgot-password";
		}
	}

	@GetMapping("/reset-password")
	public String resetPasswordPage(@RequestParam("email") String email, Model model) {
		model.addAttribute("email", email);
		return "auth/reset-password";
	}

	@PostMapping("/reset-password")
	public String handleResetPassword(@RequestParam("email") String email, @RequestParam("code") String code,
			@RequestParam("newPassword") String newPassword, Model model) {
		try {
			authenticationService.resetPassword(email, code, newPassword);
			model.addAttribute("success", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập.");
			model.addAttribute("loginRequest", new LoginRequest());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("email", email);
			model.addAttribute("error", e.getMessage());
			return "auth/reset-password";
		}
	}

	@RequestMapping(value = { "/logout", "/auth/logout" }, method = { RequestMethod.GET, RequestMethod.POST })
	public String logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
		Cookie jwtCookie = new Cookie("JWT_TOKEN", null);
		jwtCookie.setPath("/");
		jwtCookie.setHttpOnly(true);
		jwtCookie.setMaxAge(0);
		response.addCookie(jwtCookie);

		if (session != null) {
			session.invalidate();
		}

		SecurityContextHolder.clearContext();

		return "redirect:/auth/login?logout=true";
	}
}