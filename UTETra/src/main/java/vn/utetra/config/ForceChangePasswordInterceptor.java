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

	private static final java.util.regex.Pattern ORDER = java.util.regex.Pattern
			.compile("^/(?:api/)?(?:admin|manager|shipper)/orders/(\\d+)");
	private static final java.util.regex.Pattern USER = java.util.regex.Pattern.compile("^/admin/users/(\\d+)");
	private static final java.util.regex.Pattern BRANCH_STOCK = java.util.regex.Pattern
			.compile("^/(?:api/)?(?:admin|manager)/branches/(\\d+)/(?:products|toppings|stock)");
	private static final java.util.regex.Pattern BRANCH = java.util.regex.Pattern
			.compile("^/(?:api/)?(?:admin|manager)/branches/(\\d+)");

	static String linkFor(String uri, Integer actorId) {
		java.util.regex.Matcher m;
		if ((m = ORDER.matcher(uri)).find()) return "/admin/orders/" + m.group(1);
		if ((m = USER.matcher(uri)).find()) return "/admin/users/" + m.group(1);
		if ((m = BRANCH_STOCK.matcher(uri)).find()) return "/admin/branches/" + m.group(1) + "/stock";
		if ((m = BRANCH.matcher(uri)).find()) return "/admin/branches/" + m.group(1);
		if (uri.startsWith("/account/")) return actorId == null ? null : "/admin/users/" + actorId;
		if (uri.startsWith("/manager/reviews")) return "/admin/reviews";
		if (uri.startsWith("/admin/")) {
			String[] p = uri.split("/");
			return p.length > 2 ? "/admin/" + p[2] : null;
		}
		return null;
	}

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

			String action = null;
			FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
			for (Map.Entry<String, Object> e : flash.entrySet()) {
				if (("successMessage".equals(e.getKey()) || "success".equals(e.getKey())) && e.getValue() != null) {
					action = String.valueOf(e.getValue());
				}
			}
			if (action == null && request.getAttribute("activityLog") != null && response.getStatus() < 400) {
				action = String.valueOf(request.getAttribute("activityLog"));
			}
			if (action == null && uri.startsWith("/api/") && response.getStatus() < 400) {
				action = "Thao tác " + uri.replace("/api/", "").replace('/', ' ').trim();
			}
			if (action == null) return;

			ActivityLog log = new ActivityLog();
			log.setActorId(u.getId());
			String name = u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername()
					: u.getFullName() + " (" + u.getUsername() + ")";
			log.setActorName(name.length() > 150 ? name.substring(0, 150) : name);
			log.setActorRole(role.isEmpty() ? "?" : role);
			log.setAction(action.length() > 500 ? action.substring(0, 500) : action);
			log.setPath(uri.length() > 255 ? uri.substring(0, 255) : uri);
			Object link = request.getAttribute("activityLink");
			log.setLink(link != null ? String.valueOf(link) : linkFor(uri, u.getId()));
			activityLogRepository.save(log);
		} catch (RuntimeException ignored) {
		}
	}
}
