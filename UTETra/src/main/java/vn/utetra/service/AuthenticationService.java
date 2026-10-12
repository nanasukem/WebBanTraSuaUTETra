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

	public AuthenticationService(UserRepository userRepository, RoleRepository roleRepository,
			OtpCodeRepository otpCodeRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, EmailService emailService) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.otpCodeRepository = otpCodeRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.emailService = emailService;
	}

	/** Sinh mã OTP 6 chữ số (000000 - 999999) */
	private String generateOtp() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** Tạo, lưu và gửi OTP qua email */
	private void createAndSendOtp(String email, String purpose) {
		String otp = generateOtp();
		OtpCode otpCode = new OtpCode();
		otpCode.setEmail(email);
		otpCode.setCode(otp);
		otpCode.setPurpose(purpose); // 'REGISTER' hoặc 'RESET_PASSWORD' theo CHECK constraint CSDL
		otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));
		otpCodeRepository.save(otpCode);

		emailService.sendOtpEmail(email, otp);
	}

	// 1. Đăng ký -> Mục đích: 'REGISTER'
	public User signup(RegisterRequest input) {
		if (userRepository.existsByUsername(input.getUsername())) {
			throw new RuntimeException("Tên đăng nhập đã tồn tại!");
		}
		if (userRepository.existsByEmail(input.getEmail())) {
			throw new RuntimeException("Email đã được sử dụng!");
		}

		User user = new User();
		user.setUsername(input.getUsername());
		user.setPassword(passwordEncoder.encode(input.getPassword()));
		user.setFullName(input.getFullName());
		user.setEmail(input.getEmail());
		user.setPhone(input.getPhone());
		user.setIsVerified(false);
		user.setIsActive(true);

		Role userRole = roleRepository.findByName("USER")
				.orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò USER"));
		user.setRole(userRole);

		User savedUser = userRepository.save(user);

		createAndSendOtp(savedUser.getEmail(), "REGISTER");

		return savedUser;
	}

	// 2. Kích hoạt OTP Đăng ký -> Tìm theo: 'REGISTER'
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

		return true;
	}

	// 3. Đăng nhập bằng username HOẶC email
	public User authenticate(LoginRequest input) {
		String login = input.getUsername() == null ? "" : input.getUsername().trim();

		User user = userRepository.findByUsernameOrEmail(login, login)
				.orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!"));

		// Kiểm tra mật khẩu TRƯỚC, sai mật khẩu thì không tiết lộ trạng thái tài khoản
		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(user.getUsername(), input.getPassword()));
		} catch (DisabledException e) {
			throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa!");
		} catch (AuthenticationException e) {
			throw new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!");
		}

		// Đúng mật khẩu nhưng chưa kích hoạt -> báo riêng kèm email
		if (!Boolean.TRUE.equals(user.getIsVerified())) {
			throw new AccountNotVerifiedException(user.getEmail());
		}

		return user;
	}

	// 4. Quên mật khẩu -> Mục đích: 'RESET_PASSWORD'
	public void processForgotPassword(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Email chưa được đăng ký trong hệ thống!"));

		if (!Boolean.TRUE.equals(user.getIsActive())) {
			throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa!");
		}

		createAndSendOtp(email, "RESET_PASSWORD");
	}

	// 5. Đặt lại mật khẩu mới -> Tìm theo: 'RESET_PASSWORD'
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

	// 6. Gửi lại OTP kích hoạt tài khoản
	// Trả về true nếu đã gửi mã mới, false nếu mã cũ vừa gửi chưa quá 60 giây (vẫn dùng được)
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