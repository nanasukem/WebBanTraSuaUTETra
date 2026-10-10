package vn.utetra.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.OtpCode;
import vn.utetra.entity.Role;
import vn.utetra.entity.User;
import vn.utetra.repository.OtpCodeRepository;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class AuthenticationService {

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

		String otp = String.format("%06d", new Random().nextInt(999999));
		OtpCode otpCode = new OtpCode();
		otpCode.setEmail(savedUser.getEmail());
		otpCode.setCode(otp);
		otpCode.setPurpose("REGISTER");
		otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpCodeRepository.save(otpCode);

		emailService.sendOtpEmail(savedUser.getEmail(), otp);

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

	// 3. Đăng nhập
	public User authenticate(LoginRequest input) {
		User user = userRepository.findByUsername(input.getUsername())
				.orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!"));

		if (!user.getIsVerified()) {
			throw new RuntimeException("Tài khoản chưa kích hoạt OTP! Vui lòng kiểm tra Email.");
		}

		authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(input.getUsername(), input.getPassword()));

		return user;
	}

	// 4. Quên mật khẩu -> Mục đích: 'RESET_PASSWORD'
	public void processForgotPassword(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Email chưa được đăng ký trong hệ thống!"));

		if (!user.getIsActive()) {
			throw new RuntimeException("Tài khoản của bạn hiện đang bị khóa!");
		}

		String otp = String.format("%06d", new Random().nextInt(999999));
		OtpCode otpCode = new OtpCode();
		otpCode.setEmail(email);
		otpCode.setCode(otp);
		otpCode.setPurpose("RESET_PASSWORD"); // Chuẩn theo CHECK constraint CSDL
		otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		otpCodeRepository.save(otpCode);

		emailService.sendOtpEmail(email, otp);
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
}