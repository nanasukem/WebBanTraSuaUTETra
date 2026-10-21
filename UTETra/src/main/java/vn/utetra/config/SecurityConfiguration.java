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
import org.springframework.security.web.SecurityFilterChain;
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
						// KHU VỰC ADMIN: quản lý toàn hệ thống (chỉ ADMIN)
						// =========================================
						.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

						// =========================================
						// KHU VỰC MANAGER: chỉ quản lý chi nhánh của mình (chỉ MANAGER)
						// =========================================
						.requestMatchers("/manager/**", "/api/manager/**").hasRole("MANAGER")

						// =========================================
						// MUA HÀNG: chỉ khách hàng USER (giỏ hàng, sổ địa chỉ, đặt hàng, đơn của tôi)
						// Admin/Manager/Shipper là tài khoản công việc, không mua hàng
						// =========================================
						.requestMatchers("/cart/**", "/api/cart/**", "/checkout/**", "/api/checkout/**",
								"/orders/**", "/api/addresses/**").hasRole("USER")

						// =========================================
						// KHU VỰC SHIPPER
						// =========================================
						.requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")

						// Các đường dẫn còn lại yêu cầu đăng nhập
						.anyRequest().authenticated())

				// Đăng nhập bằng form
				.formLogin(form -> form.loginPage("/auth/login").loginProcessingUrl("/auth/login")
						// Thành công -> "/" : HomeController lưu session.user rồi chuyển trang theo vai trò
						.defaultSuccessUrl("/", true).failureUrl("/auth/login?error=true")
						.permitAll())

				// Quản lý session
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				// Đăng xuất
				.logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/auth/login?logout")
						.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JWT_TOKEN", "JSESSIONID").permitAll())

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