package com.fashionstore.catalog.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.dto.SizeChartRequest;
import com.fashionstore.catalog.dto.SizeChartResponse;
import com.fashionstore.catalog.dto.SizeChartRowRequest;
import com.fashionstore.catalog.dto.SizeChartRowResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.model.SizeChart;
import com.fashionstore.catalog.model.SizeChartRow;
import com.fashionstore.catalog.repository.SizeChartRepository;
import com.fashionstore.catalog.repository.SizeChartRowRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SizeChartService {

    SizeChartRepository sizeChartRepository;
    SizeChartRowRepository sizeChartRowRepository;

    @Transactional(readOnly = true)
    public List<SizeChartResponse> getAll() {
        return sizeChartRepository.findAll().stream().map(this::toResponseWithoutRows).toList();
    }

    @Transactional(readOnly = true)
    public SizeChartResponse getById(String id) {
        return toResponse(findDetail(id));
    }

    @Transactional
    public SizeChartResponse create(SizeChartRequest request) {
        SizeChart sizeChart = SizeChart.builder()
                .name(request.getName())
                .unit(request.getUnit())
                .gender(request.getGender())
                .productType(request.getProductType())
                .active(request.getActive() == null || request.getActive())
                .build();
        return toResponse(sizeChartRepository.save(sizeChart));
    }

    @Transactional
    public SizeChartResponse update(String id, SizeChartRequest request) {
        SizeChart sizeChart = findDetail(id);
        sizeChart.setName(request.getName());
        sizeChart.setUnit(request.getUnit());
        sizeChart.setGender(request.getGender());
        sizeChart.setProductType(request.getProductType());
        sizeChart.setActive(request.getActive() == null || request.getActive());
        return toResponse(sizeChartRepository.save(sizeChart));
    }

    @Transactional
    public void delete(String id) {
        sizeChartRepository.delete(findDetail(id));
    }

    @Transactional
    public SizeChartRowResponse addRow(String sizeChartId, SizeChartRowRequest request) {
        SizeChart sizeChart = findDetail(sizeChartId);
        SizeChartRow row = SizeChartRow.builder()
                .sizeChart(sizeChart)
                .sizeCode(request.getSizeCode())
                .chest(request.getChest())
                .waist(request.getWaist())
                .hip(request.getHip())
                .shoulder(request.getShoulder())
                .length(request.getLength())
                .inseam(request.getInseam())
                .build();
        return toRowResponse(sizeChartRowRepository.save(row));
    }

    @Transactional
    public SizeChartRowResponse updateRow(String sizeChartId, String rowId, SizeChartRowRequest request) {
        SizeChartRow row = findOwnedRow(sizeChartId, rowId);
        row.setSizeCode(request.getSizeCode());
        row.setChest(request.getChest());
        row.setWaist(request.getWaist());
        row.setHip(request.getHip());
        row.setShoulder(request.getShoulder());
        row.setLength(request.getLength());
        row.setInseam(request.getInseam());
        return toRowResponse(sizeChartRowRepository.save(row));
    }

    @Transactional
    public void deleteRow(String sizeChartId, String rowId) {
        sizeChartRowRepository.delete(findOwnedRow(sizeChartId, rowId));
    }

    private SizeChart findDetail(String id) {
        return sizeChartRepository.findDetailById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.SIZE_CHART_NOT_FOUND));
    }

    private SizeChartRow findOwnedRow(String sizeChartId, String rowId) {
        return sizeChartRowRepository.findById(rowId)
                .filter(row -> row.getSizeChart().getId().equals(sizeChartId))
                .orElseThrow(() -> new AppException(ProductErrorCode.SIZE_CHART_NOT_FOUND));
    }

    private SizeChartResponse toResponse(SizeChart sizeChart) {
        SizeChartResponse response = toResponseWithoutRows(sizeChart);
        response.setRows(sizeChart.getRows() == null ? List.of() : sizeChart.getRows().stream()
                .map(this::toRowResponse)
                .toList());
        return response;
    }

    private SizeChartResponse toResponseWithoutRows(SizeChart sizeChart) {
        return SizeChartResponse.builder()
                .id(sizeChart.getId())
                .name(sizeChart.getName())
                .unit(sizeChart.getUnit())
                .gender(sizeChart.getGender())
                .productType(sizeChart.getProductType())
                .active(sizeChart.getActive())
                .build();
    }

    private SizeChartRowResponse toRowResponse(SizeChartRow row) {
        return SizeChartRowResponse.builder()
                .id(row.getId())
                .sizeCode(row.getSizeCode())
                .chest(row.getChest())
                .waist(row.getWaist())
                .hip(row.getHip())
                .shoulder(row.getShoulder())
                .length(row.getLength())
                .inseam(row.getInseam())
                .build();
    }
}
