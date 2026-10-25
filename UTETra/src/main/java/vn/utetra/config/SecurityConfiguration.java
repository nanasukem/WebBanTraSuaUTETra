package vn.utetra.config;

import java.util.List;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.utetra.filter.JwtAuthenticationFilter;
import vn.utetra.service.JwtService;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private final AuthenticationProvider authenticationProvider;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
			AuthenticationProvider authenticationProvider,
			JwtService jwtService,
			UserDetailsService userDetailsService) {
		this.authenticationProvider = authenticationProvider;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		http.cors(Customizer.withDefaults())

				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/payment/vnpay/ipn", "/payment/momo/ipn", "/ws/**", "/api/auth/**")
						.ignoringRequestMatchers(request -> {
							String auth = request.getHeader("Authorization");
							return auth != null && auth.startsWith("Bearer ");
						}))

				.authorizeHttpRequests(auth -> auth

						.requestMatchers("/", "/auth/**", "/api/auth/**", "/css/**", "/js/**", "/images/**",
								"/webjars/**", "/favicon.ico", "/error")
						.permitAll()

						.requestMatchers("/menu", "/menu/**", "/category/**", "/branches", "/branches/**", "/products/**", "/api/reviews/**").permitAll()

						.requestMatchers("/payment/vnpay/return", "/payment/vnpay/ipn", "/payment/momo/return",
								"/payment/momo/ipn")
						.permitAll()

						.requestMatchers("/payment/**").hasRole("USER")

						.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

						.requestMatchers("/manager/**", "/api/manager/**").hasRole("MANAGER")

						.requestMatchers("/cart/**", "/api/cart/**", "/checkout/**", "/api/checkout/**", "/orders/**",
								"/api/addresses/**", "/user/**", "/api/favorites/**")
						.hasRole("USER")

						.requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")

						.anyRequest().authenticated())

				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) -> {
							String uri = request.getRequestURI().substring(request.getContextPath().length());
							if (uri.startsWith("/api/")) {
								response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
								response.setContentType("application/json;charset=UTF-8");
								response.getWriter().write("{\"error\":\"Vui lòng đăng nhập hoặc đính kèm JWT Token hợp lệ\"}");
							} else {
								response.sendRedirect(request.getContextPath() + "/auth/login");
							}
						})
						.accessDeniedHandler((request, response, accessDeniedException) -> {
							String uri = request.getRequestURI().substring(request.getContextPath().length());
							if (uri.startsWith("/api/")) {
								Authentication auth = SecurityContextHolder.getContext().getAuthentication();
								if (auth == null || auth instanceof AnonymousAuthenticationToken) {
									response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
									response.setContentType("application/json;charset=UTF-8");
									response.getWriter().write("{\"error\":\"Vui lòng đăng nhập hoặc đính kèm JWT Token hợp lệ\"}");
								} else {
									response.setStatus(HttpServletResponse.SC_FORBIDDEN);
									response.setContentType("application/json;charset=UTF-8");
									response.getWriter().write("{\"error\":\"Bạn không có quyền truy cập tài nguyên này\"}");
								}
							} else {
								response.sendError(HttpServletResponse.SC_FORBIDDEN);
							}
						}))

				.formLogin(form -> form
						.loginPage("/auth/login")
						.loginProcessingUrl("/auth/login")
						.successHandler((request, response, authentication) -> {
							UserDetails userDetails = (authentication.getPrincipal() instanceof UserDetails ud)
									? ud
									: userDetailsService.loadUserByUsername(authentication.getName());
							String token = jwtService.generateToken(userDetails);

							Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
							jwtCookie.setHttpOnly(true);
							jwtCookie.setPath("/");
							jwtCookie.setMaxAge((int) (jwtService.getExpirationTime() / 1000));
							response.addCookie(jwtCookie);

							response.sendRedirect(request.getContextPath() + "/login-success");
						})
						.failureUrl("/auth/login?error=true")
						.permitAll())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				.logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/auth/login?logout")
						.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JWT_TOKEN", "JSESSIONID")
						.permitAll())

				.authenticationProvider(authenticationProvider)

				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {

		CorsConfiguration configuration = new CorsConfiguration();

		configuration.setAllowedOrigins(List.of("http://localhost:8080"));

		configuration.setAllowedMethods(List.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));

		configuration.setAllowCredentials(true);

		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/**", configuration);

		return source;
	}
}