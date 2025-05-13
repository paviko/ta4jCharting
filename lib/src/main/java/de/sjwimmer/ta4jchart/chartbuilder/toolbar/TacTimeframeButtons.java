package de.sjwimmer.ta4jchart.chartbuilder.toolbar;

import de.sjwimmer.ta4jchart.chartbuilder.TacChartBuilder;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import com.limemojito.trading.model.bar.Bar.Period;
import de.sjwimmer.ta4jchart.chartbuilder.IChartBuilderAdapter;

import javax.swing.*;
import java.awt.*;

/**
 * A component that adds timeframe switching buttons to a toolbar
 */
public class TacTimeframeButtons {
    private final IChartBuilderAdapter chartBuilder;
    private final ChartPanel chartPanel;
    private final Component parent;

    /**
     * Constructor for timeframe buttons
     *
     * @param chartBuilder the chart builder that manages the chart
     * @param chartPanel the chart panel to update when timeframe changes
     * @param parent the parent component to revalidate and repaint
     */
    public TacTimeframeButtons(IChartBuilderAdapter chartBuilder, ChartPanel chartPanel, Component parent) {
        this.chartBuilder = chartBuilder;
        this.chartPanel = chartPanel;
        this.parent = parent;
    }

    /**
     * Adds timeframe buttons to the toolbar
     *
     * @param toolBar the toolbar to add buttons to
     */
    public void addToToolBar(JToolBar toolBar) {
        if (chartBuilder == null || chartBuilder.getMultiTfBarSeries() == null) {
            return; // No multi-timeframe series, no buttons
        }

        ButtonGroup timeframeGroup = new ButtonGroup();
        Period initialTimeframe = chartBuilder.getMultiTfBarSeries().getPeriod();

        for (Period tf : Period.values()) {
            JToggleButton button = new JToggleButton(tf.name());
            button.setToolTipText("Switch to " + tf.name() + " timeframe");
            if (tf == initialTimeframe) {
                button.setSelected(true);
            }
            button.addActionListener(e -> {
                if (button.isSelected()) {
                    JFreeChart newChart = chartBuilder.switchTimeframe(tf);
                    chartPanel.setChart(newChart); // Update chartPanel with the (potentially modified) chart
                    
                    // Notify TacChart about the timeframe switch to refresh dependent components
                    if (parent instanceof de.sjwimmer.ta4jchart.chartbuilder.TacChart) {
                        ((de.sjwimmer.ta4jchart.chartbuilder.TacChart) parent).onTimeframeSwitched();
                    }
                    
                    parent.revalidate();
                    parent.repaint();
                }
            });
            timeframeGroup.add(button);
            toolBar.add(button);
        }
        toolBar.addSeparator();
    }
}
