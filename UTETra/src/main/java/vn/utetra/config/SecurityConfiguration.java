
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

		http.cors(Customizer.withDefaults())

				// Giữ nguyên cách cấu hình hiện tại của dự án.
				.csrf(csrf -> csrf.disable())

				.authorizeHttpRequests(auth -> auth

						// Trang Guest, đăng nhập, đăng ký và tài nguyên tĩnh.
						.requestMatchers("/", "/auth/**", "/api/auth/**", "/css/**", "/js/**", "/images/**",
								"/webjars/**", "/favicon.ico", "/error")
						.permitAll()

						// Thực đơn, trang danh mục, chi tiết sản phẩm, trang Chi nhánh: ai cũng xem được (không cần đăng nhập).
						// Mua hàng (giỏ, đặt hàng) vẫn chỉ USER - xem quy tắc bên dưới.
						.requestMatchers("/menu", "/menu/**", "/category/**", "/branches", "/products/**").permitAll()

						// Cổng thanh toán gọi về: VNPAY / MoMo đưa khách quay lại (returnUrl) và máy chủ cổng báo kết quả (IPN).
						// Không cần đăng nhập vì dữ liệu được kiểm bằng chữ ký HMAC của cổng.
						.requestMatchers("/payment/vnpay/return", "/payment/vnpay/ipn", "/payment/momo/return",
								"/payment/momo/ipn")
						.permitAll()

						// Thanh toán đơn (tạo giao dịch, trang kết quả): khách hàng.
						.requestMatchers("/payment/**").hasRole("USER")

						// Chức năng quản trị.
						.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

						// Chức năng quản lý.
						.requestMatchers("/manager/**", "/api/manager/**").hasRole("MANAGER")

						// Giỏ hàng, thanh toán, đơn hàng, địa chỉ, trang cá nhân (profile, yêu thích, đã xem, đánh giá) của USER.
						.requestMatchers("/cart/**", "/api/cart/**", "/checkout/**", "/api/checkout/**", "/orders/**",
								"/api/addresses/**", "/user/**", "/api/favorites/**")
						.hasRole("USER")

						// Chức năng giao hàng.
						.requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")

						// Những đường dẫn còn lại cần đăng nhập.
						.anyRequest().authenticated())

				// Đăng nhập bằng form. Xong -> /login-success (HomeController): Admin/Manager vào trang quản lý, USER về trang chủ.
				.formLogin(form -> form.loginPage("/auth/login").loginProcessingUrl("/auth/login")
						.defaultSuccessUrl("/login-success", true).failureUrl("/auth/login?error=true").permitAll())

				// Sử dụng session cho giao diện Thymeleaf.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

				// Đăng xuất.
				.logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessUrl("/auth/login?logout")
						.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JWT_TOKEN", "JSESSIONID")
						.permitAll())

				// Authentication provider hiện tại.
				.authenticationProvider(authenticationProvider)

				// Giữ JWT filter hiện tại.
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
