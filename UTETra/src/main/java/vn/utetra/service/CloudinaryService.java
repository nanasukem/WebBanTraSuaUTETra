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

	// Upload ảnh lên Cloudinary và trả về URL public
	public String uploadImage(MultipartFile file, String folderName) throws IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}
		Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),
				ObjectUtils.asMap("folder", "UTETra/" + folderName));
		return uploadResult.get("secure_url").toString();
	}

	// Xóa ảnh trên Cloudinary theo Public ID / URL
	public void deleteImage(String imageUrl) {
		if (imageUrl == null || imageUrl.isEmpty())
			return;
		try {
			// Tách public_id từ URL
			String publicId = extractPublicId(imageUrl);
			if (publicId != null) {
				cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String extractPublicId(String imageUrl) {
		try {
			int folderIdx = imageUrl.indexOf("UTETra/");
			int lastDotIdx = imageUrl.lastIndexOf(".");
			if (folderIdx != -1 && lastDotIdx > folderIdx) {
				return imageUrl.substring(folderIdx, lastDotIdx);
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}
}