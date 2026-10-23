package vn.utetra.config;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import vn.utetra.entity.ActivityLog;
import vn.utetra.entity.User;
import vn.utetra.repository.ActivityLogRepository;
import vn.utetra.repository.UserRepository;

/**
 * BẢO VỆ PHIÊN ĐĂNG NHẬP + NHẬT KÝ HOẠT ĐỘNG (chạy trước / sau mỗi request):
 * 1. Mỗi request đọc lại tài khoản trong database. Admin vừa KHOÁ, XOÁ, ĐẶT LẠI MẬT KHẨU hoặc ĐỔI VAI TRÒ
 *    -> người đó bị đăng xuất NGAY ở lần bấm tiếp theo (như hệ thống thật).
 * 2. Đang dùng mật khẩu tạm do Admin cấp: chỉ vào được trang đổi mật khẩu; quá 72 giờ chưa đổi thì
 *    mật khẩu tạm hết hạn -> đăng xuất, phải nhờ Admin cấp lại.
 * 3. Thao tác thành công của Admin / Manager / Shipper (có thông báo xanh, hoặc API trả về thành công)
 *    được ghi vào nhật ký: ai làm, làm gì, lúc nào.
 */
@Configuration
public class ForceChangePasswordInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final UserRepository userRepository;
	private final ActivityLogRepository activityLogRepository;

	public ForceChangePasswordInterceptor(UserRepository userRepository, ActivityLogRepository activityLogRepository) {
		this.userRepository = userRepository;
		this.activityLogRepository = activityLogRepository;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/**").excludePathPatterns("/auth/**", "/login-success",
				"/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/error", "/payment/vnpay/**",
				"/payment/momo/**", "/ws/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		HttpSession session = request.getSession(false);
		Object o = session == null ? null : session.getAttribute("user");
		if (!(o instanceof User su)) return true;

		// 1. Tài khoản bị khoá / xoá / đặt lại mật khẩu / đổi vai trò -> đăng xuất ngay
		User db = userRepository.findById(su.getId()).orElse(null);
		String reason = null;
		if (db == null) reason = "deleted";
		else if (!Boolean.TRUE.equals(db.getIsActive())) reason = "locked";
		else if (!Objects.equals(db.getPassword(), su.getPassword())) reason = "password";
		else if (db.getRole() == null || su.getRole() == null || !Objects.equals(db.getRole().getId(), su.getRole().getId())) reason = "role";
		else if (Boolean.TRUE.equals(db.getMustChangePassword()) && db.getTempPasswordExpiresAt() != null
				&& db.getTempPasswordExpiresAt().isBefore(LocalDateTime.now())) reason = "tempExpired";
		if (reason != null) {
			session.invalidate();
			SecurityContextHolder.clearContext();
			return deny(request, response, "/auth/login?signedOut=" + reason);
		}

		// 2. Mật khẩu tạm: chỉ vào trang đổi mật khẩu
		if (Boolean.TRUE.equals(db.getMustChangePassword()) && !request.getRequestURI().startsWith(request.getContextPath() + "/account/")) {
			return deny(request, response, "/account/change-password");
		}
		return true;
	}

	private static boolean deny(HttpServletRequest request, HttpServletResponse response, String to) throws Exception {
		if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Phiên đăng nhập đã hết, vui lòng đăng nhập lại");
		} else {
			response.sendRedirect(request.getContextPath() + to);
		}
		return false;
	}

	// ================= 3. NHẬT KÝ HOẠT ĐỘNG =================

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		try {
			if (!"POST".equalsIgnoreCase(request.getMethod()) && !"PUT".equalsIgnoreCase(request.getMethod())
					&& !"DELETE".equalsIgnoreCase(request.getMethod())) return;
			String uri = request.getRequestURI().substring(request.getContextPath().length());
			boolean staff = uri.startsWith("/admin/") || uri.startsWith("/manager/") || uri.startsWith("/shipper/")
					|| uri.startsWith("/api/admin/") || uri.startsWith("/api/manager/") || uri.startsWith("/api/shipper/")
					|| uri.startsWith("/account/");
			if (!staff) return;
			HttpSession session = request.getSession(false);
			Object o = session == null ? null : session.getAttribute("user");
			if (!(o instanceof User u)) return;
			String role = u.getRole() == null || u.getRole().getName() == null ? "" : u.getRole().getName().toUpperCase();
			if (uri.startsWith("/account/") && "USER".equals(role)) return;

			// Nội dung: lấy đúng thông báo xanh vừa hiện cho người dùng ("Đã báo hết Bạc xỉu tại chi nhánh.")
			String action = null;
			FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
			for (Map.Entry<String, Object> e : flash.entrySet()) {
				if (("successMessage".equals(e.getKey()) || "success".equals(e.getKey())) && e.getValue() != null) {
					action = String.valueOf(e.getValue());
				}
			}
			// API (Quầy đơn hàng...): controller đặt sẵn câu dễ đọc, ví dụ "Xác nhận đơn #12 (bắt đầu pha chế)"
			if (action == null && request.getAttribute("activityLog") != null && response.getStatus() < 400) {
				action = String.valueOf(request.getAttribute("activityLog"));
			}
			if (action == null && uri.startsWith("/api/") && response.getStatus() < 400) {
				action = "Thao tác " + uri.replace("/api/", "").replace('/', ' ').trim();
			}
			if (action == null) return; // lỗi / không có kết quả thì không ghi

			ActivityLog log = new ActivityLog();
			log.setActorId(u.getId());
			String name = u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername()
					: u.getFullName() + " (" + u.getUsername() + ")";
			log.setActorName(name.length() > 150 ? name.substring(0, 150) : name);
			log.setActorRole(role.isEmpty() ? "?" : role);
			log.setAction(action.length() > 500 ? action.substring(0, 500) : action);
			log.setPath(uri.length() > 255 ? uri.substring(0, 255) : uri);
			activityLogRepository.save(log);
		} catch (RuntimeException ignored) {
			// ghi nhật ký lỗi thì bỏ qua, không ảnh hưởng thao tác chính
		}
	}
}
