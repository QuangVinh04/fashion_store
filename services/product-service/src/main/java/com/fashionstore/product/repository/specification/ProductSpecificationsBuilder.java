package com.fashionstore.product.repository.specification;

import com.fashionstore.product.model.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecificationsBuilder {

    public final List<SpecSearchCriteria> params;

    public ProductSpecificationsBuilder() {
        params = new ArrayList<>();
    }

    // khi khÃ´ng cÃ³ toÃ¡n tá»­ or
    public ProductSpecificationsBuilder with(final String key, final String operation, Object value, final String prefix, final String suffix) {
        return with(null, key, operation, value, prefix, suffix);
    }

    public ProductSpecificationsBuilder with(final String orPredicate, final String key, final String operation, Object value, final String prefix, final String suffix) {
        //Chuyá»ƒn Ä‘á»•i kÃ½ tá»± Ä‘áº§u tiÃªn cá»§a phÃ©p toÃ¡n (operation) thÃ nh SearchOperation (enum).
        SearchOperation searchOperation = SearchOperation.getSimpleOperation(operation.charAt(0));
        if (searchOperation != null) {
            if (searchOperation == SearchOperation.EQUALITY) {
                String strValue = value.toString();

                // Tá»± Ä‘á»™ng kiá»ƒm tra dáº¥u * trong giÃ¡ trá»‹ truyá»n vÃ o
                boolean startWithAsterisk = strValue.startsWith(SearchOperation.ZERO_OR_MORE_REGEX);
                boolean endWithAsterisk = strValue.endsWith(SearchOperation.ZERO_OR_MORE_REGEX);

                if (startWithAsterisk && endWithAsterisk) {
                    searchOperation = SearchOperation.CONTAINS;
                    value = strValue.substring(1, strValue.length() - 1); // Cáº¯t bá» 2 dáº¥u *
                } else if (startWithAsterisk) {
                    searchOperation = SearchOperation.ENDS_WITH;
                    value = strValue.substring(1); // Cáº¯t bá» dáº¥u * á»Ÿ Ä‘áº§u
                } else if (endWithAsterisk) {
                    searchOperation = SearchOperation.STARTS_WITH;
                    value = strValue.substring(0, strValue.length() - 1); // Cáº¯t bá» dáº¥u * á»Ÿ cuá»‘i
                }
            }
            params.add(new SpecSearchCriteria(orPredicate, key, searchOperation, value));
        }
        return this;
    }

    // táº¡o 1 Specification káº¿t há»£p cÃ¡c Ä‘iá»u kiá»‡n
    public Specification<Product> build() {
        if (params.isEmpty())
            return null;

        // Táº¡o danh sÃ¡ch cÃ¡c Specification tá»« params, loáº¡i bá» cÃ¡c giÃ¡ trá»‹ null tiá»m áº©n
        List<ProductSpecification> specs = params.stream()
                .map(ProductSpecification::new)
                .toList();

        // khá»Ÿi táº¡o 1 Specification tá»« Ä‘k tÃ¬m kiáº¿m Ä‘áº§u tiÃªn
        Specification<Product> result = specs.get(0);

        for (int i = 1; i < params.size(); i++) {
            // Kiá»ƒm tra xem tiÃªu chÃ­ hiá»‡n táº¡i cÃ³ pháº£i lÃ  toÃ¡n tá»­ OR khÃ´ng
            if (params.get(i).isOrPredicate()) {
                result = result.or(specs.get(i));
            } else {
                result = result.and(specs.get(i));
            }
        }
        return result;
    }

    public ProductSpecificationsBuilder with(ProductSpecification spec) {
        params.add(spec.getCriteria());
        return this;
    }

    public ProductSpecificationsBuilder with(SpecSearchCriteria criteria) {
        params.add(criteria);
        return this;
    }
}

