package vn.utetra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.JwtService;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
			UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String jwt = null;

		// 1. Kiểm tra Header "Authorization: Bearer <token>"
		final String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			jwt = authHeader.substring(7);
		} else {
			// 2. Nếu không có header, đọc từ Cookie "JWT_TOKEN" (Phục vụ Thymeleaf)
			if (request.getCookies() != null) {
				for (Cookie cookie : request.getCookies()) {
					if ("JWT_TOKEN".equals(cookie.getName())) {
						jwt = cookie.getValue();
						break;
					}
				}
			}
		}

		if (jwt != null && !jwt.isBlank()) {
			try {
				final String username = jwtService.extractUsername(jwt);

				if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
					UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

					if (jwtService.isTokenValid(jwt, userDetails)) {
						UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
								userDetails, null, userDetails.getAuthorities());
						authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authToken);

						// 3. ĐỒNG BỘ ĐỐI TƯỢNG USER VÀO HTTP SESSION CHO THYMELEAF
						HttpSession session = request.getSession(true);
						if (session.getAttribute("user") == null) {
							userRepository.findByUsername(username)
									.ifPresent(user -> session.setAttribute("user", user));
						}
					}
				}
			} catch (Exception e) {
				// Nếu JWT lỗi hoặc hết hạn -> Xóa cookie JWT_TOKEN ngay lập tức để tránh lặp
				// lỗi
				Cookie cookie = new Cookie("JWT_TOKEN", null);
				cookie.setPath("/");
				cookie.setMaxAge(0);
				response.addCookie(cookie);
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}
}