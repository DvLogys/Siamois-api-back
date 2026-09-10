package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.UiTableView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UiViewRepository extends JpaRepository<UiTableView, Long> {

    List<UiTableView> findAllByOwnerIdAndResourceTypeOrderByTitleAsc(Long ownerId, String resourceType);

}
