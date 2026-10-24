package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.response.CartRes;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CartService;
import vn.utetra.service.CurrentUserService;
import vn.utetra.dto.response.ProductOptionsRes;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

	private final CartService cartService;
	private final CurrentUserService currentUserService;
	private final vn.utetra.service.MenuBranchService menuBranchService;

	public CartApiController(CartService cartService, CurrentUserService currentUserService,
			vn.utetra.service.MenuBranchService menuBranchService) {
		this.menuBranchService = menuBranchService;
		this.cartService = cartService;
		this.currentUserService = currentUserService;
	}

	public record QuantityReq(Integer quantity) {
	}

	@GetMapping
	public CartRes getCart(Principal principal, HttpSession session) {
		return cartService.getCart(currentUserService.requireUserId(principal, session));
	}

	@GetMapping("/options/{productId}")
	public ProductOptionsRes options(@PathVariable Integer productId, HttpSession session) {
		return cartService.getOptions(productId, menuBranchService.currentId(session));
	}

	@PostMapping
	public CartRes add(@Valid @RequestBody CartAddReq req, Principal principal, HttpSession session) {
		return cartService.addItem(currentUserService.requireUserId(principal, session), req);
	}

	@PutMapping("/{id}")
	public CartRes updateQuantity(@PathVariable Integer id, @RequestBody QuantityReq body, Principal principal,
			HttpSession session) {
		return cartService.updateQuantity(currentUserService.requireUserId(principal, session), id, body.quantity());
	}

	@PutMapping("/{id}/options")
	public CartRes updateOptions(@PathVariable Integer id, @Valid @RequestBody CartAddReq req, Principal principal,
			HttpSession session) {
		return cartService.updateOptions(currentUserService.requireUserId(principal, session), id, req);
	}

	@DeleteMapping("/{id}")
	public CartRes remove(@PathVariable Integer id, Principal principal, HttpSession session) {
		return cartService.removeItem(currentUserService.requireUserId(principal, session), id);
	}

	@DeleteMapping
	public CartRes clear(Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		cartService.clear(userId);
		return cartService.getCart(userId);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
		return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
		String msg = e.getBindingResult().getFieldErrors().isEmpty() ? "Dữ liệu không hợp lệ"
				: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
		return ResponseEntity.badRequest().body(Map.of("message", msg));
	}
}