package vn.utetra.controller.api;

import java.security.Principal;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.FavoriteService;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteApiController {

	private final FavoriteService favoriteService;
	private final CurrentUserService currentUserService;

	public FavoriteApiController(FavoriteService favoriteService, CurrentUserService currentUserService) {
		this.favoriteService = favoriteService;
		this.currentUserService = currentUserService;
	}

	@PostMapping("/{productId}/toggle")
	public Map<String, Object> toggle(@PathVariable("productId") Integer productId, Principal principal,
			HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		boolean favorite = favoriteService.toggle(userId, productId);
		return Map.of("favorite", favorite, "count", favoriteService.countOfProduct(productId));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode())
				.body(Map.of("message", e.getReason() == null ? "Vui lòng đăng nhập" : e.getReason()));
	}
}
