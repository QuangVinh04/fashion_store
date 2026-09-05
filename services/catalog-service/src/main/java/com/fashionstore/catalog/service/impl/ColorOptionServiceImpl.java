package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.catalog.dto.ColorOptionRequest;
import com.fashionstore.catalog.dto.ColorOptionResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.mapper.ColorOptionMapper;
import com.fashionstore.catalog.model.option.ColorOption;
import com.fashionstore.catalog.repository.ColorOptionRepository;
import com.fashionstore.catalog.service.ColorOptionService;
import com.fashionstore.catalog.service.ProductAtrributeService;
import com.fashionstore.catalog.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ColorOptionServiceImpl implements ColorOptionService {
    ColorOptionRepository colorOptionRepository;
    ColorOptionMapper colorOptionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ColorOptionResponse> getAllOptionActive() {
        List<ColorOption> colorOptions = colorOptionRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
        return colorOptions.stream()
                .map(colorOptionMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<List<ColorOptionResponse>> getAllOptions(Pageable pageable) {
        Page<ColorOption> optionPage = colorOptionRepository.findAll(pageable);
        List<ColorOptionResponse> responses = optionPage.stream()
                .map(colorOptionMapper::toResponse)
                .toList();
        return PageResponse.<List<ColorOptionResponse>>builder().pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(optionPage.getTotalPages())
                .items(responses)
                .build();
    }

    @Override
    public ColorOptionResponse create(ColorOptionRequest request) {
        String normalized = StringUtils.normalizeCode(request.getName());
        if(colorOptionRepository.existsByNormalizedName(normalized)){
            throw new AppException(ProductErrorCode.OPTION_ALREADY_EXIST);
        }
        ColorOption option = ColorOption.builder()
                .name(request.getName())
                .normalizedName(normalized)
                .colorHex(request.getColorHex())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        return colorOptionMapper.toResponse(colorOptionRepository.save(option));
    }

    @Override
    public ColorOptionResponse update(ColorOptionRequest request, String Id) {
        ColorOption option = colorOptionRepository.findById(Id)
                .orElseThrow(() -> new AppException(ProductErrorCode.OPTION_NOT_FOUND));

        String normalized = StringUtils.normalizeCode(request.getName());
        if(colorOptionRepository.existsByNormalizedName(normalized)){
            throw new AppException(ProductErrorCode.OPTION_ALREADY_EXIST);
        }
        colorOptionMapper.updateColorOption(option, request);
        option.setNormalizedName(normalized);


        return colorOptionMapper.toResponse(colorOptionRepository.save(option));
    }

    @Override
    public void delete(String Id) {

    }
}

