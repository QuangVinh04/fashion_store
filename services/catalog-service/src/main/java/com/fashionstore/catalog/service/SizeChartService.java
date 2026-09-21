package com.fashionstore.catalog.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.dto.SizeChartRequest;
import com.fashionstore.catalog.dto.SizeChartResponse;
import com.fashionstore.catalog.dto.SizeChartRowRequest;
import com.fashionstore.catalog.dto.SizeChartRowResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.entity.SizeChart;
import com.fashionstore.catalog.entity.SizeChartRow;
import com.fashionstore.catalog.repository.SizeChartRepository;
import com.fashionstore.catalog.repository.SizeChartRowRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Manages size charts and their measurement rows for storefront and backoffice use.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SizeChartService {

    SizeChartRepository sizeChartRepository;
    SizeChartRowRepository sizeChartRowRepository;

    /** Public listing — /api/v1/size-charts is permitAll, so deactivated charts stay out of it. */
    @Transactional(readOnly = true)
    public List<SizeChartResponse> getAll() {
        return sizeChartRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponseWithoutRows)
                .toList();
    }

    /** Backoffice listing — has to include deactivated charts so a delete can be undone. */
    @Transactional(readOnly = true)
    public List<SizeChartResponse> getAllForAdmin() {
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

    /**
     * product.size_chart_id has no FK, so removing the row would silently leave products pointing
     * at a chart that no longer exists and their /size-chart endpoint would 404 forever.
     * Deactivate instead; the reference stays resolvable.
     */
    @Transactional
    public void delete(String id) {
        SizeChart sizeChart = findDetail(id);
        sizeChart.setActive(false);
        sizeChartRepository.save(sizeChart);
    }

    /**
     * Adds a measurement row after validating its optional fit-finder ranges.
     *
     * @param sizeChartId owning size-chart identifier
     * @param request row measurements and fit ranges
     * @return the persisted row
     */
    @Transactional
    public SizeChartRowResponse addRow(String sizeChartId, SizeChartRowRequest request) {
        validateFitRanges(request);
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
                .heightMin(request.getHeightMin())
                .heightMax(request.getHeightMax())
                .weightMin(request.getWeightMin())
                .weightMax(request.getWeightMax())
                .build();
        return toRowResponse(sizeChartRowRepository.save(row));
    }

    /**
     * Updates a measurement row after validating its optional fit-finder ranges.
     *
     * @param sizeChartId owning size-chart identifier
     * @param rowId row identifier
     * @param request replacement measurements and fit ranges
     * @return the updated row
     */
    @Transactional
    public SizeChartRowResponse updateRow(String sizeChartId, String rowId, SizeChartRowRequest request) {
        validateFitRanges(request);
        SizeChartRow row = findOwnedRow(sizeChartId, rowId);
        row.setSizeCode(request.getSizeCode());
        row.setChest(request.getChest());
        row.setWaist(request.getWaist());
        row.setHip(request.getHip());
        row.setShoulder(request.getShoulder());
        row.setLength(request.getLength());
        row.setInseam(request.getInseam());
        row.setHeightMin(request.getHeightMin());
        row.setHeightMax(request.getHeightMax());
        row.setWeightMin(request.getWeightMin());
        row.setWeightMax(request.getWeightMax());
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
                .heightMin(row.getHeightMin())
                .heightMax(row.getHeightMax())
                .weightMin(row.getWeightMin())
                .weightMax(row.getWeightMax())
                .build();
    }

    private void validateFitRanges(SizeChartRowRequest request) {
        validateRange(request.getHeightMin(), request.getHeightMax());
        validateRange(request.getWeightMin(), request.getWeightMax());
    }

    private void validateRange(BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new AppException(ProductErrorCode.SIZE_CHART_RANGE_INVALID);
        }
    }
}
