package vn.utetra.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

	private final Cloudinary cloudinary;

	public CloudinaryService(Cloudinary cloudinary) {
		this.cloudinary = cloudinary;
	}

	/**
	 * Upload ảnh lên Cloudinary với thư mục mặc định 'products'
	 */
	public String uploadImage(MultipartFile file) {
		return uploadImage(file, "products");
	}

	/**
	 * Upload ảnh lên Cloudinary với thư mục tùy chỉnh (branches, categories,
	 * products,...) Tự động xử lý IOException thành RuntimeException để các Service
	 * khác gọi gọn gàng.
	 */
	public String uploadImage(MultipartFile file, String folder) {
		if (file == null || file.isEmpty()) {
			return null;
		}

		try {
			Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),
					ObjectUtils.asMap("folder", "utetra/" + folder, "resource_type", "auto"));
			return uploadResult.get("secure_url").toString();
		} catch (IOException e) {
			throw new RuntimeException("Lỗi khi tải ảnh lên Cloudinary: " + e.getMessage(), e);
		}
	}

	/**
	 * Xóa ảnh trên Cloudinary từ URL
	 */
	public void deleteImage(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return;
		}
		try {
			String publicId = extractPublicIdFromUrl(imageUrl);
			if (publicId != null) {
				cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
			}
		} catch (Exception e) {
			System.err.println("Lỗi khi xóa ảnh Cloudinary: " + e.getMessage());
		}
	}

	/**
	 * Trích xuất Public ID từ URL Cloudinary
	 */
	private String extractPublicIdFromUrl(String imageUrl) {
		try {
			int uploadIndex = imageUrl.indexOf("/upload/");
			if (uploadIndex == -1)
				return null;

			String pathAfterUpload = imageUrl.substring(uploadIndex + 8);
			if (pathAfterUpload.startsWith("v")) {
				int firstSlash = pathAfterUpload.indexOf('/');
				if (firstSlash != -1) {
					pathAfterUpload = pathAfterUpload.substring(firstSlash + 1);
				}
			}
			int lastDot = pathAfterUpload.lastIndexOf('.');
			return (lastDot != -1) ? pathAfterUpload.substring(0, lastDot) : pathAfterUpload;
		} catch (Exception e) {
			return null;
		}
	}
}