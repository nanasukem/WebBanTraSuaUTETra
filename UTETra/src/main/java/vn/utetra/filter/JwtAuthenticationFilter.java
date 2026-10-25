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
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
		String path = request.getServletPath();
		return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
				|| path.startsWith("/webjars/") || path.equals("/favicon.ico") || path.equals("/")
				|| path.startsWith("/menu") || path.startsWith("/auth/") || path.startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String path = request.getRequestURI().substring(request.getContextPath().length());
		String jwt = null;
		boolean hasBearerHeader = false;

		final String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			hasBearerHeader = true;
			jwt = authHeader.substring(7).trim();
		} else if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if ("JWT_TOKEN".equals(cookie.getName())) {
					jwt = cookie.getValue();
					break;
				}
			}
		}

		if (jwt != null && !jwt.isBlank()) {
			try {
				final String username = jwtService.extractUsername(jwt);

				if (username != null && (hasBearerHeader || SecurityContextHolder.getContext().getAuthentication() == null)) {
					UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

					if (jwtService.isTokenValid(jwt, userDetails)) {
						UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
								userDetails, null, userDetails.getAuthorities());
						authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authToken);

						HttpSession session = request.getSession(true);
						Object sessionUser = session.getAttribute("user");
						if (!(sessionUser instanceof User u) || !username.equals(u.getUsername())) {
							userRepository.findByUsername(username)
									.ifPresent(user -> session.setAttribute("user", user));
						}
					} else {
						SecurityContextHolder.clearContext();
					}
				}
			} catch (Exception e) {
				Cookie cookie = new Cookie("JWT_TOKEN", null);
				cookie.setPath("/");
				cookie.setHttpOnly(true);
				cookie.setMaxAge(0);
				response.addCookie(cookie);
				SecurityContextHolder.clearContext();
			}
		} else if (hasBearerHeader || (path.startsWith("/api/") && !path.startsWith("/api/auth/"))) {
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}
}