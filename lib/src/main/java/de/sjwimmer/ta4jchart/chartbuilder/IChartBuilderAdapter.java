package de.sjwimmer.ta4jchart.chartbuilder;

import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import com.limemojito.trading.model.bar.Bar.Period;
// Potentially add other methods if TacChart needs more generic access
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;


public interface IChartBuilderAdapter {
    void setInitialChartViewport(JFreeChart chart, int chartPanelWidth);
    BarSeries getCurrentBarSeries(); // Full series for current TF
    IBarSeriesMultiTf getMultiTfBarSeries();
    JFreeChart switchTimeframe(Period newTimeframe);

    // Add this method if TacChartMouseHandler needs to be managed by TacChart/Builder
    default DynamicWindowOHLCDataset getDynamicOHLCDataset() {
        return null;
    }
}
