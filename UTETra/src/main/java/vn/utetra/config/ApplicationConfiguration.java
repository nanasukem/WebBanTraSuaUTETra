package vn.utetra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.utetra.repository.UserRepository;

import java.util.Collections;

@Configuration
public class ApplicationConfiguration {

	private final UserRepository userRepository;

	public ApplicationConfiguration(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Bean
	public UserDetailsService userDetailsService() {
		return username -> userRepository.findByUsername(username)
				.map(user -> new org.springframework.security.core.userdetails.User(user.getUsername(),
						user.getPassword(), user.getIsActive(), true, true, true,
						Collections.singletonList(() -> "ROLE_" + user.getRole().getName())))
				.orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng!"));
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}
}