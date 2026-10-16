package vn.utetra.config;

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

import java.util.List;

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
		return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
				// Trang lỗi
				.requestMatchers("/error").permitAll()

				// Trang công khai + Auth (Login, Register, OTP, Reset Pass, Logout)
				.requestMatchers("/", "/menu/**", "/auth/**", "/api/auth/**", "/login/**").permitAll()

				// Tài nguyên tĩnh
				.requestMatchers("/images/**", "/js/**", "/css/**", "/favicon.ico").permitAll()

				// Khách được XEM sản phẩm, danh mục, topping (chỉ GET)
				.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/toppings/**")
				.permitAll()

				// WebSocket (bắt tay kết nối)
				.requestMatchers("/ws/**").permitAll()

				// ADMIN + MANAGER
				.requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("ADMIN", "MANAGER")

				// VENDOR
				.requestMatchers("/vendor/**", "/api/vendor/**").hasRole("VENDOR")

				// SHIPPER
				.requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")

				// Còn lại: phải đăng nhập
				.anyRequest().authenticated())
				// Chuyển sang IF_REQUIRED để giữ Session cho giao diện Web Thymeleaf
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				// Cấu hình Đăng xuất chuẩn
				.logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/menu").invalidateHttpSession(true)
						.deleteCookies("JWT_TOKEN", "JSESSIONID"))
				.authenticationProvider(authenticationProvider)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class).build();
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