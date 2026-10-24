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

				.csrf(csrf -> csrf.disable())

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

				.formLogin(form -> form.loginPage("/auth/login").loginProcessingUrl("/auth/login")
						.defaultSuccessUrl("/login-success", true).failureUrl("/auth/login?error=true").permitAll())

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