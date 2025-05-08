package de.sjwimmer.ta4jchart.chartbuilder.utils;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;

import java.util.List;

/**
 * Utility methods for chart manipulation
 */
public class TacChartUtils {

    /**
     * Applies auto range state to all range axes in the chart
     *
     * @param chart the chart to apply auto range to
     * @param enableAutoRange whether to enable or disable auto range
     */
    public static void applyAutoRangeState(JFreeChart chart, boolean enableAutoRange) {
        if (chart == null) {
            return;
        }
        org.jfree.chart.plot.Plot plot = chart.getPlot();

        if (plot instanceof CombinedDomainXYPlot) {
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) plot;
            // JFreeChart API uses raw types for subplots list, so suppress warnings
            @SuppressWarnings("unchecked")
            List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null) {
                for (XYPlot subplot : subplots) {
                    if (subplot != null) {
                        ValueAxis rangeAxis = subplot.getRangeAxis();
                        if (rangeAxis != null) {
                            rangeAxis.setAutoRange(enableAutoRange);
                        }
                        // For secondary range axes, if any
                        for (int i = 0; i < subplot.getRangeAxisCount(); i++) {
                            ValueAxis axis = subplot.getRangeAxis(i);
                            if (axis != null) {
                                axis.setAutoRange(enableAutoRange);
                            }
                        }
                    }
                }
            }
        } else if (plot instanceof XYPlot) { // Handles charts with a single XYPlot
            XYPlot xyPlot = (XYPlot) plot;
            ValueAxis rangeAxis = xyPlot.getRangeAxis();
            if (rangeAxis != null) {
                rangeAxis.setAutoRange(enableAutoRange);
            }
            // For secondary range axes, if any
            for (int i = 0; i < xyPlot.getRangeAxisCount(); i++) {
                ValueAxis axis = xyPlot.getRangeAxis(i);
                if (axis != null) {
                    axis.setAutoRange(enableAutoRange);
                }
            }
        }
        // JFreeChart typically repaints automatically when axis properties change.
        // If not, chart.fireChartChanged() or chartPanel.repaint() might be needed,
        // but usually setAutoRange triggers necessary updates.
    }
}
