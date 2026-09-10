package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.SizeChartResponse;
import com.fashionstore.catalog.model.SizeChart;
import com.fashionstore.catalog.repository.SizeChartRepository;
import com.fashionstore.catalog.repository.SizeChartRowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SizeChartServiceTest {

    @Mock
    SizeChartRepository sizeChartRepository;
    @Mock
    SizeChartRowRepository sizeChartRowRepository;

    @InjectMocks
    SizeChartService sizeChartService;

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
}
