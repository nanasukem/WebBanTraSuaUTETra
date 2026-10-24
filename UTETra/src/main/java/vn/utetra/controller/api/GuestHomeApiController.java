package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.service.GuestHomeService;

import java.util.List;

@RestController
@RequestMapping("/api/guest")
public class GuestHomeApiController {

	private final GuestHomeService guestHomeService;

	public GuestHomeApiController(GuestHomeService guestHomeService) {
		this.guestHomeService = guestHomeService;
	}

	@GetMapping("/categories")
	public ResponseEntity<List<Category>> getCategories() {
		return ResponseEntity.ok(guestHomeService.getAllCategories());
	}

	@GetMapping("/products")
	public ResponseEntity<List<Product>> getProducts(@RequestParam(required = false) Integer categoryId) {
		return ResponseEntity.ok(guestHomeService.getProducts(categoryId));
	}

	@GetMapping("/best-sellers")
	public ResponseEntity<List<Product>> getBestSellers() {
		return ResponseEntity.ok(guestHomeService.getBestSellerProducts());
	}

	@GetMapping("/product/{id}")
	public ResponseEntity<Product> getProductDetail(@PathVariable Integer id) {
		return ResponseEntity.ok(guestHomeService.getProductDetail(id));
	}
}