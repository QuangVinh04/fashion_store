package com.fashionstore.product.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.product.dto.*;
import com.fashionstore.product.mapper.ProductAttributeMapper;
import com.fashionstore.product.mapper.ProductAttributeOptionMapper;
import com.fashionstore.product.model.attribute.ProductAttribute;
import com.fashionstore.product.model.attribute.ProductAttributeOption;
import com.fashionstore.product.model.attribute.ProductAttributeValue;
import com.fashionstore.product.repository.ProductAttributeOptionRepository;
import com.fashionstore.product.repository.ProductAttributeRepository;
import com.fashionstore.product.repository.ProductAttributeValueRepository;
import com.fashionstore.product.service.ProductAtrributeService;
import com.fashionstore.product.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductOptionServiceImpl implements ProductAtrributeService {



}

