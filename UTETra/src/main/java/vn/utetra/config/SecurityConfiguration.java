package vn.utetra.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.utetra.filter.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private final AuthenticationProvider authenticationProvider;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
			AuthenticationProvider authenticationProvider) {
		this.authenticationProvider = authenticationProvider;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	/**
	 * Chuyển hướng sau khi đăng nhập thành công theo vai trò.
	 */
	@Bean
	public AuthenticationSuccessHandler authenticationSuccessHandler() {
		return (request, response, authentication) -> {
			String redirectUrl;

			if (hasRole(authentication, "ROLE_ADMIN")) {
				redirectUrl = "/admin/dashboard";
			} else if (hasRole(authentication, "ROLE_MANAGER")) {
				redirectUrl = "/manager/dashboard";
			} else if (hasRole(authentication, "ROLE_VENDOR")) {
				redirectUrl = "/vendor/dashboard";
			} else if (hasRole(authentication, "ROLE_SHIPPER")) {
				redirectUrl = "/shipper/dashboard";
			} else {
				redirectUrl = "/";
			}

			response.sendRedirect(request.getContextPath() + redirectUrl);
		};
	}

	private boolean hasRole(Authentication authentication, String role) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> role.equals(authority.getAuthority()));
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http.cors(Customizer.withDefaults())

				// Giữ nguyên cấu hình CSRF hiện tại của dự án.
				// Lưu ý: CSRF đang bị vô hiệu hóa.
				.csrf(csrf -> csrf.disable())

				.authorizeHttpRequests(auth -> auth

						// Tài nguyên tĩnh
						.requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/error")
						.permitAll()

						// Trang công khai và xác thực
						.requestMatchers("/", "/menu", "/menu/**", "/auth/**", "/api/auth/**").permitAll()

						// API xem dữ liệu công khai
						.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/toppings/**")
						.permitAll()

						// WebSocket
						.requestMatchers("/ws/**").permitAll()

						// =========================================
						// KHU VỰC MANAGER
						// ADMIN và MANAGER được phép truy cập
						// =========================================
						.requestMatchers("/manager/**", "/api/manager/**").hasAnyRole("ADMIN", "MANAGER")

						// =========================================
						// QUẢN LÝ NGƯỜI DÙNG: CHỈ ADMIN
						// Phải đặt trước quy tắc /admin/**
						// =========================================
						.requestMatchers("/admin/users", "/admin/users/**", "/api/admin/users", "/api/admin/users/**")
						.hasRole("ADMIN")

						// =========================================
						// CÁC KHU VỰC QUẢN TRỊ KHÁC
						// ADMIN và MANAGER
						// =========================================
						.requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("ADMIN", "MANAGER")

						// =========================================
						// KHU VỰC VENDOR
						// =========================================
						.requestMatchers("/vendor/**", "/api/vendor/**").hasRole("VENDOR")

						// =========================================
						// KHU VỰC SHIPPER
						// =========================================
						.requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")

						// Các đường dẫn còn lại yêu cầu đăng nhập
						.anyRequest().authenticated())

				// Đăng nhập bằng form
				.formLogin(form -> form.loginPage("/auth/login").loginProcessingUrl("/auth/login")
						.successHandler(authenticationSuccessHandler()).failureUrl("/auth/login?error=true")
						.permitAll())

				// Quản lý session
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				// Đăng xuất
				.logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/auth/login?logout")
						.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID").permitAll())

				// Authentication provider
				.authenticationProvider(authenticationProvider)

				// JWT filter
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

				.build();
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