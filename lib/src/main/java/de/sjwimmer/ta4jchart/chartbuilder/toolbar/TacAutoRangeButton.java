package de.sjwimmer.ta4jchart.chartbuilder.toolbar;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class TacAutoRangeButton extends JToggleButton implements ActionListener {

    private final JFreeChart chart;

    public TacAutoRangeButton(JFreeChart chart) {
        super("Auto Range Y");
        this.chart = chart;
        setToolTipText("Enable/Disable automatic adjustment of vertical (Y) axis range");
        setSelected(true); // Default to auto-ranging enabled, as is typical for JFreeChart axes
        addActionListener(this);

        // Apply initial state based on isSelected()
        // This ensures that if the default axis state was different, it's aligned with the button
        applyAutoRangeState(isSelected());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        applyAutoRangeState(isSelected());
    }

    public void applyAutoRangeState(boolean enableAutoRange) {
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