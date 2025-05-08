package de.sjwimmer.ta4jchart.chartbuilder.toolbar;

import de.sjwimmer.ta4jchart.chartbuilder.utils.TacChartUtils;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;

/**
 * A component that adds zoom in/out buttons to a toolbar
 */
public class TacZoomButtons {
    private final ChartPanel chartPanel;
    private final TacAutoRangeButton tacAutoRangeButton;

    /**
     * Constructor for zoom buttons
     *
     * @param chartPanel the chart panel to zoom in/out
     * @param tacAutoRangeButton the auto range button to apply auto range after zooming
     */
    public TacZoomButtons(ChartPanel chartPanel, TacAutoRangeButton tacAutoRangeButton) {
        this.chartPanel = chartPanel;
        this.tacAutoRangeButton = tacAutoRangeButton;
    }

    /**
     * Adds zoom buttons to the toolbar
     *
     * @param toolBar the toolbar to add buttons to
     */
    public void addToToolBar(JToolBar toolBar) {
        JButton zoomInButton = new JButton("+");
        zoomInButton.setToolTipText("Zoom In");
        zoomInButton.addActionListener(e -> {
            JFreeChart chart = chartPanel.getChart();
            if (chart == null) {
                return;
            }
            org.jfree.chart.plot.Plot plot = chart.getPlot();
            ValueAxis domainAxis = null;
            if (plot instanceof CombinedDomainXYPlot) {
                domainAxis = ((CombinedDomainXYPlot) plot).getDomainAxis();
            } else if (plot instanceof XYPlot) {
                domainAxis = ((XYPlot) plot).getDomainAxis();
            }
            if (domainAxis instanceof DateAxis) {
                DateAxis dateAxis = (DateAxis) domainAxis;
                double lower = dateAxis.getRange().getLowerBound();
                double upper = dateAxis.getRange().getUpperBound();
                double length = upper - lower;
                double newLength = length * 0.5;
                double newLower = upper - newLength;
                dateAxis.setRange(newLower, upper);
                if (tacAutoRangeButton.isSelected()) {
                    // After zooming, if Y-axes are set to auto-range, force them to readjust
                    TacChartUtils.applyAutoRangeState(chart, true);
                }
            }
        });
        toolBar.add(zoomInButton);

        JButton zoomOutButton = new JButton("-");
        zoomOutButton.setToolTipText("Zoom Out");
        zoomOutButton.addActionListener(e -> {
            JFreeChart chart = chartPanel.getChart();
            if (chart == null) {
                return;
            }
            org.jfree.chart.plot.Plot plot = chart.getPlot();
            ValueAxis domainAxis = null;
            if (plot instanceof CombinedDomainXYPlot) {
                domainAxis = ((CombinedDomainXYPlot) plot).getDomainAxis();
            } else if (plot instanceof XYPlot) {
                domainAxis = ((XYPlot) plot).getDomainAxis();
            }
            if (domainAxis instanceof DateAxis) {
                DateAxis dateAxis = (DateAxis) domainAxis;
                double lower = dateAxis.getRange().getLowerBound();
                double upper = dateAxis.getRange().getUpperBound();
                double length = upper - lower;
                double newLength = length * 2.0;
                double newLower = upper - newLength;
                dateAxis.setRange(newLower, upper);
                if (tacAutoRangeButton.isSelected()) {
                    // After zooming, if Y-axes are set to auto-range, force them to readjust
                    TacChartUtils.applyAutoRangeState(chart, true);
                }
            }
        });
        toolBar.add(zoomOutButton);
    }
}
