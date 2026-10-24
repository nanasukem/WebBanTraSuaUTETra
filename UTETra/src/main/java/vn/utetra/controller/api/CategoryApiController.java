package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Category;
import vn.utetra.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryApiController {

	private final ProductService productService;

	public CategoryApiController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public ResponseEntity<List<Category>> getAllCategories() {
		List<Category> categories = productService.getAllCategories();
		return ResponseEntity.ok(categories);
	}
}