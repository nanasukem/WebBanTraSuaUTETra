package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.service.AuthenticationService;
import vn.utetra.service.JwtService;
import vn.utetra.service.RefreshTokenService;
import vn.utetra.config.AuthCookies;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

	private final AuthenticationService authenticationService;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final RefreshTokenService refreshTokenService;

	public AuthApiController(AuthenticationService authenticationService, JwtService jwtService,
			UserDetailsService userDetailsService, RefreshTokenService refreshTokenService) {
		this.authenticationService = authenticationService;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.refreshTokenService = refreshTokenService;
	}

	@PostMapping("/signup")
	public ResponseEntity<?> register(@RequestBody RegisterRequest registerUser) {
		try {
			User registeredUser = authenticationService.signup(registerUser);
			Map<String, Object> response = new HashMap<>();
			response.put("message", "Đăng ký thành công! Vui lòng kiểm tra email để lấy mã OTP.");
			response.put("email", registeredUser.getEmail());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/verify-otp")
	public ResponseEntity<?> verifyOtp(@RequestParam("email") String email, @RequestParam("code") String code) {
		try {
			authenticationService.verifyOtp(email, code);
			return ResponseEntity.ok(Map.of("message", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập."));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/login")
	public ResponseEntity<?> authenticate(@RequestBody LoginRequest loginUser) {
		try {
			User authenticatedUser = authenticationService.authenticate(loginUser);

			UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getUsername());
			String jwtToken = jwtService.generateToken(userDetails);

			SecurityContextHolder.clearContext();

			Map<String, Object> response = new HashMap<>();
			response.put("token", jwtToken);
			response.put("expiresIn", jwtService.getExpirationTime());
			response.put("refreshToken", refreshTokenService.issue(authenticatedUser));
			response.put("refreshExpiresIn", refreshTokenService.getExpirationTime());
			response.put("username", authenticatedUser.getUsername());
			response.put("role", authenticatedUser.getRole().getName());

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(@RequestBody(required = false) Map<String, String> body) {
		String raw = body == null ? null : body.get("refreshToken");
		Optional<RefreshTokenService.Rotation> rotation = refreshTokenService.rotate(raw, false);
		if (rotation.isEmpty()) {
			return ResponseEntity.status(401)
					.body(Map.of("error", "Refresh token không hợp lệ hoặc đã hết hạn, vui lòng đăng nhập lại"));
		}
		UserDetails userDetails = userDetailsService.loadUserByUsername(rotation.get().user().getUsername());
		Map<String, Object> response = new HashMap<>();
		response.put("token", jwtService.generateToken(userDetails));
		response.put("expiresIn", jwtService.getExpirationTime());
		response.put("refreshToken", rotation.get().newToken());
		response.put("refreshExpiresIn", refreshTokenService.getExpirationTime());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestParam("email") String email) {
		try {
			authenticationService.processForgotPassword(email);
			return ResponseEntity.ok(Map.of("message", "Mã OTP khôi phục mật khẩu đã được gửi đến email của bạn."));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/reset-password")
	public ResponseEntity<?> resetPassword(@RequestParam("email") String email, @RequestParam("code") String code,
			@RequestParam("newPassword") String newPassword) {
		try {
			authenticationService.resetPassword(email, code, newPassword);
			return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công!"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body,
			HttpServletRequest request, HttpServletResponse response) {
		refreshTokenService.revoke(body == null ? null : body.get("refreshToken"));
		refreshTokenService.revoke(AuthCookies.read(request, AuthCookies.REFRESH));
		AuthCookies.clear(response, AuthCookies.ACCESS);
		AuthCookies.clear(response, AuthCookies.REFRESH);

		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}

		SecurityContextHolder.clearContext();

		return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công!"));
	}
}