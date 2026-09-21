package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.SizeChartRowRequest;
import com.fashionstore.catalog.dto.SizeChartResponse;
import com.fashionstore.catalog.entity.SizeChart;
import com.fashionstore.catalog.entity.SizeChartRow;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.repository.SizeChartRepository;
import com.fashionstore.catalog.repository.SizeChartRowRepository;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies size-chart lifecycle behavior and fit-range mapping.
 */
@ExtendWith(MockitoExtension.class)
class SizeChartServiceTest {

    @Mock
    private SizeChartRepository sizeChartRepository;
    @Mock
    private SizeChartRowRepository sizeChartRowRepository;

    @InjectMocks
    private SizeChartService sizeChartService;

    /**
     * product.size_chart_id has no FK, so a hard delete succeeds and leaves products pointing at a
     * row that no longer exists — their /size-chart endpoint then 404s forever. Deactivating keeps
     * the reference resolvable while hiding the chart from the storefront.
     */
    @Test
    void deleteDeactivatesTheSizeChartInsteadOfRemovingTheRow() {
        SizeChart sizeChart = SizeChart.builder().name("Men tops").unit("cm").active(true).build();
        when(sizeChartRepository.findDetailById("sc1")).thenReturn(Optional.of(sizeChart));

        sizeChartService.delete("sc1");

        assertThat(sizeChart.getActive()).isFalse();
        verify(sizeChartRepository).save(sizeChart);
        verify(sizeChartRepository, never()).delete(any(SizeChart.class));
    }

    /** /api/v1/size-charts is permitAll, so the public listing must not show deactivated charts. */
    @Test
    void publicListOnlyReturnsActiveSizeCharts() {
        SizeChart active = SizeChart.builder().name("Men tops").unit("cm").active(true).build();
        when(sizeChartRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(active));

        List<SizeChartResponse> charts = sizeChartService.getAll();

        assertThat(charts).singleElement().satisfies(chart -> {
            assertThat(chart.getName()).isEqualTo("Men tops");
            assertThat(chart.getActive()).isTrue();
        });
        verify(sizeChartRepository, never()).findAll();
    }

    @Test
    void adminListIncludesInactiveSizeCharts() {
        SizeChart inactive = SizeChart.builder().name("Legacy").unit("cm").active(false).build();
        when(sizeChartRepository.findAll()).thenReturn(List.of(inactive));

        List<SizeChartResponse> charts = sizeChartService.getAllForAdmin();

        assertThat(charts).singleElement().satisfies(chart -> {
            assertThat(chart.getName()).isEqualTo("Legacy");
            assertThat(chart.getActive()).isFalse();
        });
    }

    @Test
    void addRowPersistsAndReturnsFitFinderRanges() {
        SizeChart sizeChart = SizeChart.builder().name("Men tops").unit("cm").active(true).build();
        sizeChart.setId("sc1");
        SizeChartRowRequest request = SizeChartRowRequest.builder()
                .sizeCode("M")
                .heightMin(new BigDecimal("165"))
                .heightMax(new BigDecimal("170"))
                .weightMin(new BigDecimal("55"))
                .weightMax(new BigDecimal("62"))
                .build();
        when(sizeChartRepository.findDetailById("sc1")).thenReturn(Optional.of(sizeChart));
        when(sizeChartRowRepository.save(any(SizeChartRow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = sizeChartService.addRow("sc1", request);

        assertThat(response.getHeightMin()).isEqualByComparingTo("165");
        assertThat(response.getHeightMax()).isEqualByComparingTo("170");
        assertThat(response.getWeightMin()).isEqualByComparingTo("55");
        assertThat(response.getWeightMax()).isEqualByComparingTo("62");
        verify(sizeChartRowRepository).save(any(SizeChartRow.class));
    }

    @Test
    void getByIdReturnsFitFinderRangesToStorefront() {
        SizeChartRow row = SizeChartRow.builder()
                .sizeCode("L")
                .heightMin(new BigDecimal("170"))
                .heightMax(new BigDecimal("175"))
                .weightMin(new BigDecimal("63"))
                .weightMax(new BigDecimal("70"))
                .build();
        SizeChart sizeChart = SizeChart.builder()
                .name("Men tops")
                .unit("cm")
                .active(true)
                .rows(List.of(row))
                .build();
        when(sizeChartRepository.findDetailById("sc1")).thenReturn(Optional.of(sizeChart));

        SizeChartResponse response = sizeChartService.getById("sc1");

        assertThat(response.getRows()).singleElement().satisfies(sizeRow -> {
            assertThat(sizeRow.getHeightMin()).isEqualByComparingTo("170");
            assertThat(sizeRow.getHeightMax()).isEqualByComparingTo("175");
            assertThat(sizeRow.getWeightMin()).isEqualByComparingTo("63");
            assertThat(sizeRow.getWeightMax()).isEqualByComparingTo("70");
        });
    }

    @Test
    void updateRowReplacesFitFinderRanges() {
        SizeChart sizeChart = SizeChart.builder().name("Men tops").unit("cm").active(true).build();
        sizeChart.setId("sc1");
        SizeChartRow row = SizeChartRow.builder().sizeChart(sizeChart).sizeCode("M").build();
        row.setId("row1");
        SizeChartRowRequest request = SizeChartRowRequest.builder()
                .sizeCode("M")
                .heightMin(new BigDecimal("166"))
                .heightMax(new BigDecimal("171"))
                .weightMin(new BigDecimal("56"))
                .weightMax(new BigDecimal("64"))
                .build();
        when(sizeChartRowRepository.findById("row1")).thenReturn(Optional.of(row));
        when(sizeChartRowRepository.save(row)).thenReturn(row);

        var response = sizeChartService.updateRow("sc1", "row1", request);

        assertThat(response.getHeightMin()).isEqualByComparingTo("166");
        assertThat(response.getHeightMax()).isEqualByComparingTo("171");
        assertThat(response.getWeightMin()).isEqualByComparingTo("56");
        assertThat(response.getWeightMax()).isEqualByComparingTo("64");
    }

    @Test
    void addRowRejectsAnInvertedFitFinderRange() {
        SizeChartRowRequest request = SizeChartRowRequest.builder()
                .sizeCode("M")
                .heightMin(new BigDecimal("171"))
                .heightMax(new BigDecimal("165"))
                .build();

        assertThatThrownBy(() -> sizeChartService.addRow("sc1", request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ProductErrorCode.SIZE_CHART_RANGE_INVALID));

        verify(sizeChartRowRepository, never()).save(any(SizeChartRow.class));
    }
}
