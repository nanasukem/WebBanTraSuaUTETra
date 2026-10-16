package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.utetra.entity.Address;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {

	List<Address> findByUserIdOrderByIsDefaultDesc(Integer userId);

	Optional<Address> findByUserIdAndIsDefaultTrue(Integer userId);

	@Modifying
	@Query("UPDATE Address a SET a.isDefault = false WHERE a.user.id = :userId")
	void resetDefaultAddressByUserId(Integer userId);
}