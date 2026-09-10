package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.MediaFile;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class MediaFileSpecifications {

    private MediaFileSpecifications() {
    }

    public static Specification<MediaFile> filter(String ownerId,
                                                  String keyword,
                                                  MediaType mediaType,
                                                  String folder,
                                                  MediaStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("ownerId"), ownerId));
            predicates.add(criteriaBuilder.equal(root.get("status"), status == null ? MediaStatus.ACTIVE : status));

            if (mediaType != null) {
                predicates.add(criteriaBuilder.equal(root.get("mediaType"), mediaType));
            }
            if (folder != null && !folder.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("folder")), folder.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                query.distinct(true);
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("originalFilename")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("altText")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.join("tags", JoinType.LEFT)), pattern)
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
