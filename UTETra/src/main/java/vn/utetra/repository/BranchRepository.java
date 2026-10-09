package vn.utetra.repository;

import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {

    List<Branch> findByStatusOrderByNameAsc(BranchStatus status);

    List<Branch> findByOwner_Id(Integer ownerId);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    /** Tìm theo tên/địa chỉ + lọc trạng thái (tham số null = bỏ qua điều kiện đó) */
    @Query("""
           SELECT b FROM Branch b JOIN FETCH b.owner
           WHERE (:keyword IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                   OR LOWER(b.address) LIKE LOWER(CONCAT('%', :keyword, '%')))
             AND (:status IS NULL OR b.status = :status)
           ORDER BY b.createdAt DESC
           """)
    List<Branch> search(@Param("keyword") String keyword, @Param("status") BranchStatus status);
}