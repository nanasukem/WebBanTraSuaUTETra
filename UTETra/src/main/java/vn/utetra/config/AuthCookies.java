package vn.utetra.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class AuthCookies {

	public static final String ACCESS = "JWT_TOKEN";
	public static final String REFRESH = "REFRESH_TOKEN";
	public static final String EXPIRED_FLAG = "SESSION_EXPIRED";

	private AuthCookies() {
	}

	public static String read(HttpServletRequest request, String name) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public static void set(HttpServletResponse response, String name, String value, long maxAgeMillis) {
		Cookie cookie = new Cookie(name, value);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge((int) Math.max(0, maxAgeMillis / 1000));
		cookie.setAttribute("SameSite", "Lax");
		response.addCookie(cookie);
	}

	public static void clear(HttpServletResponse response, String name) {
		Cookie cookie = new Cookie(name, null);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		response.addCookie(cookie);
	}
}
