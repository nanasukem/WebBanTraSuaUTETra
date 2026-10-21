package vn.utetra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.ReviewMedia;

public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, Integer> {

	List<ReviewMedia> findByReviewIdInOrderByIdAsc(List<Integer> reviewIds);
}
