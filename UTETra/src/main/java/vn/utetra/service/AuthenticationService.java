package vn.utetra.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.OtpCode;
import vn.utetra.entity.Role;
import vn.utetra.entity.User;
import vn.utetra.exception.AccountNotVerifiedException;
import vn.utetra.repository.OtpCodeRepository;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthenticationService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int OTP_EXPIRE_MINUTES = 5;
	private static final int RESEND_COOLDOWN_SECONDS = 60;

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final OtpCodeRepository otpCodeRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final EmailService emailService;
	private final NotificationService notificationService;

	public AuthenticationService(UserRepository userRepository, RoleRepository roleRepository,
			OtpCodeRepository otpCodeRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, EmailService emailService,
			NotificationService notificationService) {
		this.notificationService = notificationService;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.otpCodeRepository = otpCodeRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.emailService = emailService;
	}

	private String generateOtp() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	private void createAndSendOtp(String email, String purpose) {
		String otp = generateOtp();
		OtpCode otpCode = new OtpCode();
		otpCode.setEmail(email);
		otpCode.setCode(otp);
		otpCode.setPurpose(purpose);
		otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));
		otpCodeRepository.save(otpCode);

		emailService.sendOtpEmail(email, otp);
	}

	public User signup(RegisterRequest input) {
		String username = input.getUsername() == null ? "" : input.getUsername().trim();
		String email = input.getEmail() == null ? "" : input.getEmail().trim().toLowerCase();
		String phone = UserRepository.normalizePhone(input.getPhone());
		String fullName = input.getFullName() == null ? "" : input.getFullName().trim().replaceAll("\\s+", " ");
		if (!username.matches("[A-Za-z0-9_.]{3,50}")) {
			throw new RuntimeException("Tên đăng nhập 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu _ hoặc dấu chấm");
		}
		if (input.getPassword() == null || input.getPassword().length() < 6) {
			throw new RuntimeException("Mật khẩu tối thiểu 6 ký tự");
		}
		if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
			throw new RuntimeException("Email không hợp lệ");
		}
		if (phone.isEmpty()) {
			throw new RuntimeException("Vui lòng nhập số điện thoại");
		}
		if (!phone.matches("0\\d{9}")) {
			throw new RuntimeException("Số điện thoại phải có 10 số, bắt đầu bằng 0");
		}
		if (userRepository.existsByUsername(username)) {
			throw new RuntimeException("Tên đăng nhập đã tồn tại!");
		}
		if (userRepository.existsByEmail(email)) {
			throw new RuntimeException("Email đã được sử dụng!");
		}
		if (userRepository.existsByPhone(phone)) {
			throw new RuntimeException("Số điện thoại đã được dùng cho tài khoản khác!");
		}

		User user = new User();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(input.getPassword()));
		user.setFullName(fullName.isEmpty() ? null : fullName);
		user.setEmail(email);
		user.setPhone(phone);
		user.setIsVerified(false);
		user.setIsActive(true);

		Role userRole = roleRepository.findByName("USER")
				.orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò USER"));
		user.setRole(userRole);

		User savedUser = userRepository.save(user);

		createAndSendOtp(savedUser.getEmail(), "REGISTER");

		return savedUser;
	}

	public boolean verifyOtp(String email, String code) {
		OtpCode otpCode = otpCodeRepository.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, "REGISTER")
				.orElseThrow(() -> new RuntimeException("Mã OTP không hợp lệ hoặc đã hết hạn!"));

		if (otpCode.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new RuntimeException("Mã OTP đã hết hạn!");
		}

		if (!otpCode.getCode().equals(code)) {
			throw new RuntimeException("Mã OTP không chính xác!");
		}

		otpCode.setUsed(true);
		otpCodeRepository.save(otpCode);

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
		user.setIsVerified(true);
		userRepository.save(user);

		if (user.getRole() != null && "USER".equalsIgnoreCase(user.getRole().getName())) {
			notificationService.notifyAdmins("USER", "Khách mới đăng ký: " + user.getUsername(),
					(user.getFullName() == null ? "" : user.getFullName() + " · ") + user.getEmail(), "/admin/users",
					"NEW_USER:" + user.getId());
		}

		return true;
	}

	public User authenticate(LoginRequest input) {
		String login = input.getUsername() == null ? "" : input.getUsername().trim();

		User user = userRepository.findByLogin(login)
				.orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!"));

		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(user.getUsername(), input.getPassword()));
		} catch (DisabledException e) {
			throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa!");
		} catch (AuthenticationException e) {
			throw new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!");
		}

		if (!Boolean.TRUE.equals(user.getIsVerified())) {
			throw new AccountNotVerifiedException(user.getEmail());
		}

		return user;
	}

	public void processForgotPassword(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Email chưa được đăng ký trong hệ thống!"));

		if (!Boolean.TRUE.equals(user.getIsActive())) {
			throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa!");
		}

		createAndSendOtp(email, "RESET_PASSWORD");
	}

	public boolean resetPassword(String email, String code, String newPassword) {
		OtpCode otpCode = otpCodeRepository
				.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, "RESET_PASSWORD")
				.orElseThrow(() -> new RuntimeException("Mã OTP không hợp lệ hoặc đã hết hạn!"));

		if (otpCode.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new RuntimeException("Mã OTP đã hết hạn!");
		}

		if (!otpCode.getCode().equals(code)) {
			throw new RuntimeException("Mã OTP không chính xác!");
		}

		otpCode.setUsed(true);
		otpCodeRepository.save(otpCode);

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);

		return true;
	}

	public boolean resendRegisterOtp(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Email chưa được đăng ký trong hệ thống!"));

		if (Boolean.TRUE.equals(user.getIsVerified())) {
			throw new RuntimeException("Tài khoản đã được kích hoạt, vui lòng đăng nhập!");
		}

		boolean recentlySent = otpCodeRepository
				.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, "REGISTER")
				.map(o -> o.getCreatedAt() != null
						&& o.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS)))
				.orElse(false);

		if (recentlySent) {
			return false;
		}

		createAndSendOtp(email, "REGISTER");
		return true;
	}
}