package vn.utetra.service;

import vn.utetra.dto.CategoryFormDto;
import vn.utetra.dto.ProductDto;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.Topping;

import java.io.IOException;
import java.util.List;

public interface AdminProductService {
	List<Product> getAllProducts();

	Product getProductById(Integer id);

	Product createProduct(ProductDto dto) throws IOException;

	Product updateProduct(Integer id, ProductDto dto) throws IOException;

	void deleteProduct(Integer id);

	List<Category> getAllCategories();

	Category getCategoryById(Integer id);

	Category saveCategory(CategoryFormDto dto) throws IOException;

	void deleteCategory(Integer id);

}