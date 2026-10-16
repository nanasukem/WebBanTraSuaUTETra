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

	public String uploadImage(MultipartFile file, String folderName) {
		if (file == null || file.isEmpty())
			return null;
		try {
			Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),
					ObjectUtils.asMap("folder", "utetra/" + folderName));
			return uploadResult.get("secure_url").toString();
		} catch (IOException e) {
			throw new RuntimeException("Lỗi upload ảnh lên Cloudinary: " + e.getMessage());
		}
	}

	public void deleteImage(String imageUrl) {
		if (imageUrl == null || !imageUrl.contains("cloudinary"))
			return;
		try {
			String publicId = extractPublicId(imageUrl);
			if (publicId != null) {
				cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
			}
		} catch (Exception ignored) {
		}
	}

	private String extractPublicId(String imageUrl) {
		try {
			int start = imageUrl.indexOf("utetra/");
			int end = imageUrl.lastIndexOf(".");
			return imageUrl.substring(start, end);
		} catch (Exception e) {
			return null;
		}
	}
}