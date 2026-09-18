package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductCategory;
import com.fashionstore.catalog.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;


@Getter
@AllArgsConstructor
public class ProductSpecification implements Specification<Product> {

    private SpecSearchCriteria criteria;



    @Override
    public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String key = criteria.getKey();
        Object value = criteria.getValue();

        // Xá»­ lÃ½ Ä‘áº·c biá»‡t cho má»™t sá»‘ trÆ°á»ng
        return switch (key) {
            case "category" -> {
                query.distinct(true);
                Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories");
                Join<ProductCategory, ?> categoryJoin = productCategoryJoin.join("category");
                yield cb.like(cb.lower(categoryJoin.get("name")), "%" + value.toString().toLowerCase() + "%");
            }

            case "categoryId" -> {
                query.distinct(true);
                Join<Product, ProductCategory> productCategoryJoin = root.join("productCategories");
                Join<ProductCategory, ?> categoryJoin = productCategoryJoin.join("category");
                yield cb.equal(categoryJoin.get("id"), value.toString());
            }

            case "priceRange" -> {
                String rangeStr = criteria.getValue().toString();


                if (rangeStr.endsWith("+")) {
                    BigDecimal min = new BigDecimal(rangeStr.replace("+", ""));
                    yield cb.greaterThanOrEqualTo(root.get("basePrice"), min);
                } else {
                    String[] bounds = rangeStr.split("-");
                    if (bounds.length == 2) {
                        BigDecimal min = new BigDecimal(bounds[0]);
                        BigDecimal max = new BigDecimal(bounds[1]);
                        yield cb.between(root.get("basePrice"), min, max);
                    } else yield null;
                }
            }


            case "minPrice" -> {
                BigDecimal min = (value instanceof BigDecimal bd) ? bd : new BigDecimal(value.toString());
                yield cb.greaterThanOrEqualTo(root.get("basePrice"), min);
            }

            case "maxPrice" -> {
                BigDecimal max = (value instanceof BigDecimal bd) ? bd : new BigDecimal(value.toString());
                yield cb.lessThanOrEqualTo(root.get("basePrice"), max);
            }

            case "brandId" -> cb.equal(root.get("brand").get("id"), value.toString());

            case "brand" -> {
                Join<Product, ?> brandJoin = root.join("brand", JoinType.LEFT);
                yield cb.or(
                        cb.equal(brandJoin.get("id"), value.toString()),
                        cb.like(cb.lower(brandJoin.get("name")), "%" + value.toString().toLowerCase() + "%"),
                        cb.equal(cb.lower(brandJoin.get("slug")), value.toString().toLowerCase())
                );
            }

            case "gender" -> {
                com.fashionstore.catalog.entity.enumeration.Gender g;
                try {
                    g = com.fashionstore.catalog.entity.enumeration.Gender.valueOf(value.toString().toUpperCase());
                } catch (Exception e) {
                    yield null;
                }
                yield cb.equal(root.get("gender"), g);
            }

            case "material" -> {
                query.distinct(true);
                Join<Product, com.fashionstore.catalog.entity.attribute.ProductAttributeValue> attrValueJoin = root.join("attributeValues");
                Join<com.fashionstore.catalog.entity.attribute.ProductAttributeValue, com.fashionstore.catalog.entity.attribute.ProductAttribute> attrJoin = attrValueJoin.join("attribute");
                yield cb.and(
                        cb.or(
                                cb.equal(cb.lower(attrJoin.get("code")), "material"),
                                cb.like(cb.lower(attrJoin.get("name")), "%chất liệu%"),
                                cb.like(cb.lower(attrJoin.get("name")), "%material%")
                        ),
                        cb.like(cb.lower(attrValueJoin.get("value")), "%" + value.toString().toLowerCase() + "%")
                );
            }

            case "color" -> {
                query.distinct(true);
                Join<Product, ProductVariant> variantJoin = root.join("variants");
                // A deactivated variant must not keep its product in a public facet result.
                yield cb.and(
                        cb.equal(cb.lower(variantJoin.get("colorDisplay")), value.toString().toLowerCase()),
                        cb.isTrue(variantJoin.get("active")));
            }

            case "size" -> {
                query.distinct(true);
                Join<Product, ProductVariant> variantJoin = root.join("variants");
                // A deactivated variant must not keep its product in a public facet result.
                yield cb.and(
                        cb.equal(cb.lower(variantJoin.get("sizeDisplay")), value.toString().toLowerCase()),
                        cb.isTrue(variantJoin.get("active")));
            }

            case "price" -> getPredicate(cb, root.get("basePrice"), value);

            default -> getPredicate(cb, root.get(key), value);
        };
    }

    private Predicate getPredicate(CriteriaBuilder cb, Path path, Object value) {
        Class<?> type = path.getJavaType();

        // Convert giÃ¡ trá»‹ String tá»« URL sang Ä‘Ãºng kiá»ƒu dá»¯ liá»‡u cá»§a cá»™t
        Object castedValue = value;
        if (type != String.class) {
            if (type == BigDecimal.class) castedValue = new BigDecimal(value.toString());
            else if (type == Integer.class || type == int.class) castedValue = Integer.parseInt(value.toString());
            else if (type == Long.class || type == long.class) castedValue = Long.parseLong(value.toString());
            else if (type == Double.class || type == double.class) castedValue = Double.parseDouble(value.toString());
        }

        return switch (criteria.getOperation()) {
            case EQUALITY -> cb.equal(path, castedValue);
            case NEGATION -> cb.notEqual(path, castedValue);
            case GREATER_THAN -> cb.greaterThan(path, (Comparable) castedValue);
            case LESS_THAN -> cb.lessThan(path, (Comparable) castedValue);
            case LIKE, CONTAINS -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase() + "%");
            case STARTS_WITH -> cb.like(cb.lower(path.as(String.class)), value.toString().toLowerCase() + "%");
            case ENDS_WITH -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase());
            default -> null;
        };
    }
}

