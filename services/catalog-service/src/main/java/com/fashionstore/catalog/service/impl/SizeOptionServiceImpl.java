package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.dto.SizeOptionRequest;
import com.fashionstore.catalog.dto.SizeOptionResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.mapper.SizeOptionMapper;
import com.fashionstore.catalog.model.option.SizeOption;
import com.fashionstore.catalog.repository.SizeOptionRepository;
import com.fashionstore.catalog.service.ProductAtrributeService;
import com.fashionstore.catalog.service.SizeOptionService;
import com.fashionstore.catalog.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SizeOptionServiceImpl implements SizeOptionService {
    SizeOptionRepository SizeOptionRepository;
    SizeOptionMapper SizeOptionMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SizeOptionResponse> getAllOptionActive() {
        List<SizeOption> SizeOptions = SizeOptionRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
        return SizeOptions.stream()
                .map(SizeOptionMapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<List<SizeOptionResponse>> getAllOptions(Pageable pageable) {
        Page<SizeOption> optionPage = SizeOptionRepository.findAll(pageable);
        List<SizeOptionResponse> responses = optionPage.stream()
                .map(SizeOptionMapper::toResponse)
                .toList();
        return PageResponse.<List<SizeOptionResponse>>builder().pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(optionPage.getTotalPages())
                .items(responses)
                .build();
    }

    @Override
    public SizeOptionResponse create(SizeOptionRequest request) {
        String normalized = StringUtils.normalizeCode(request.getName());
        if(SizeOptionRepository.existsByNormalizedName(normalized)){
            throw new AppException(ProductErrorCode.OPTION_ALREADY_EXIST);
        }
        SizeOption option = SizeOption.builder()
                .name(request.getName())
                .normalizedName(normalized)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        return SizeOptionMapper.toResponse(SizeOptionRepository.save(option));
    }

    @Override
    public SizeOptionResponse update(SizeOptionRequest request, String Id) {
        SizeOption option = SizeOptionRepository.findById(Id)
                .orElseThrow(() -> new AppException(ProductErrorCode.OPTION_NOT_FOUND));

        String normalized = StringUtils.normalizeCode(request.getName());
        if(SizeOptionRepository.existsByNormalizedName(normalized)){
            throw new AppException(ProductErrorCode.OPTION_ALREADY_EXIST);
        }
        SizeOptionMapper.updateSizeOption(option, request);
        option.setNormalizedName(normalized);


        return SizeOptionMapper.toResponse(SizeOptionRepository.save(option));
    }

    @Override
    public void delete(String Id) {

    }


}

