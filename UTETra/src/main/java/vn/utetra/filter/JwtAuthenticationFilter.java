package vn.utetra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.utetra.config.AuthCookies;
import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.JwtService;
import vn.utetra.service.RefreshTokenService;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final UserRepository userRepository;
	private final RefreshTokenService refreshTokenService;

	public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
			UserRepository userRepository, RefreshTokenService refreshTokenService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepository;
		this.refreshTokenService = refreshTokenService;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
				|| path.startsWith("/webjars/") || path.equals("/favicon.ico") || path.startsWith("/ws/")
				|| path.startsWith("/payment/vnpay/") || path.startsWith("/payment/momo/")
				|| path.startsWith("/api/auth/") || path.equals("/login-success")
				|| (path.equals("/auth/login") && "POST".equalsIgnoreCase(request.getMethod()));
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String jwt = null;
		boolean hasBearerHeader = false;

		final String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			hasBearerHeader = true;
			jwt = authHeader.substring(7).trim();
		} else {
			jwt = AuthCookies.read(request, AuthCookies.ACCESS);
		}

		if (jwt != null && !jwt.isBlank()) {
			try {
				final String username = jwtService.extractUsername(jwt);
				Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
				boolean needAuth = existingAuth == null || !existingAuth.isAuthenticated()
						|| existingAuth instanceof AnonymousAuthenticationToken
						|| !username.equals(existingAuth.getName());

				if (username != null && (hasBearerHeader || needAuth)) {
					UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

					if (jwtService.isTokenValid(jwt, userDetails)) {
						authenticate(request, userDetails, hasBearerHeader);
					} else if (hasBearerHeader || !tryRefresh(request, response)) {
						clearAuthAndSession(request, response);
					}
				} else if (username != null) {
					HttpSession session = request.getSession(false);
					if (session != null && session.getAttribute("user") == null) {
						userRepository.findByUsername(username).ifPresent(user -> {
							session.setAttribute("user", user);
							session.setAttribute("currentUser", user);
						});
					}
				}
			} catch (Exception e) {
				if (hasBearerHeader || !tryRefresh(request, response)) {
					clearAuthAndSession(request, response);
				}
			}
		} else if (hasBearerHeader || !tryRefresh(request, response)) {

			Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
			boolean hasAuthenticatedContext = existingAuth != null && existingAuth.isAuthenticated()
					&& !(existingAuth instanceof AnonymousAuthenticationToken);

			HttpSession session = request.getSession(false);
			boolean hasLoggedInSession = session != null && (session.getAttribute("user") != null
					|| session.getAttribute("currentUser") != null
					|| session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null);

			if (hasBearerHeader || hasAuthenticatedContext || hasLoggedInSession
					|| AuthCookies.read(request, AuthCookies.REFRESH) != null) {
				clearAuthAndSession(request, response);
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean tryRefresh(HttpServletRequest request, HttpServletResponse response) {
		String raw = AuthCookies.read(request, AuthCookies.REFRESH);
		if (raw == null || raw.isBlank()) {
			return false;
		}
		try {
			Optional<RefreshTokenService.Rotation> rotation = refreshTokenService.rotate(raw, true);
			if (rotation.isEmpty()) {
				return false;
			}
			UserDetails userDetails = userDetailsService.loadUserByUsername(rotation.get().user().getUsername());
			AuthCookies.set(response, AuthCookies.ACCESS, jwtService.generateToken(userDetails),
					jwtService.getExpirationTime());
			if (rotation.get().newToken() != null) {
				AuthCookies.set(response, AuthCookies.REFRESH, rotation.get().newToken(),
						refreshTokenService.getExpirationTime());
			}
			authenticate(request, userDetails, false);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void authenticate(HttpServletRequest request, UserDetails userDetails, boolean hasBearerHeader) {
		String username = userDetails.getUsername();
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null,
				userDetails.getAuthorities());
		authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authToken);
		SecurityContextHolder.setContext(context);

		HttpSession session = request.getSession(true);
		if (!hasBearerHeader) {
			session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		}
		Object sessionUser = session.getAttribute("user");
		if (!(sessionUser instanceof User u) || !username.equals(u.getUsername())) {
			userRepository.findByUsername(username).ifPresent(user -> {
				session.setAttribute("user", user);
				session.setAttribute("currentUser", user);
			});
		}
	}

	private void clearAuthAndSession(HttpServletRequest request, HttpServletResponse response) {
		AuthCookies.clear(response, AuthCookies.ACCESS);

		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}

		SecurityContextHolder.clearContext();

		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			AuthCookies.clear(response, AuthCookies.REFRESH);
			AuthCookies.set(response, AuthCookies.EXPIRED_FLAG, "1", 600_000);
			request.setAttribute("sessionExpired", Boolean.TRUE);
		}
	}
}
