package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.service.AuthenticationService;
import vn.utetra.service.JwtService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

	private final AuthenticationService authenticationService;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public AuthApiController(AuthenticationService authenticationService, JwtService jwtService,
			UserDetailsService userDetailsService) {
		this.authenticationService = authenticationService;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@PostMapping("/signup")
	public ResponseEntity<User> register(@RequestBody RegisterRequest registerUser) {
		User registeredUser = authenticationService.signup(registerUser);
		return ResponseEntity.ok(registeredUser);
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> authenticate(@RequestBody LoginRequest loginUser) {
		User authenticatedUser = authenticationService.authenticate(loginUser);

		UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getUsername());
		String jwtToken = jwtService.generateToken(userDetails);

		Map<String, Object> response = new HashMap<>();
		response.put("token", jwtToken);
		response.put("expiresIn", jwtService.getExpirationTime());

		return ResponseEntity.ok(response);
	}
}