package de.sjwimmer.ta4jchart.chartbuilder.toolbar;

import de.sjwimmer.ta4jchart.chartbuilder.utils.TacChartUtils;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
        TacChartUtils.applyAutoRangeState(chart, isSelected());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TacChartUtils.applyAutoRangeState(chart, isSelected());
    }

    /**
     * Applies auto range state to all range axes in the chart
     * 
     * @param enableAutoRange whether to enable or disable auto range
     * @deprecated Use TacChartUtils.applyAutoRangeState instead
     */
    @Deprecated
    public void applyAutoRangeState(boolean enableAutoRange) {
        TacChartUtils.applyAutoRangeState(chart, enableAutoRange);
    }
}