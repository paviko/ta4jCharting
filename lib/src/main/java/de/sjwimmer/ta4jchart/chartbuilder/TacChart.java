package de.sjwimmer.ta4jchart.chartbuilder;

import de.sjwimmer.ta4jchart.chartbuilder.crosshair.TacChartMouseHandler;
import de.sjwimmer.ta4jchart.chartbuilder.data.DataPanel;
import de.sjwimmer.ta4jchart.chartbuilder.data.TacDataTableModel;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacAutoRangeButton;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacShowBuySellSignals;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacShowDataButton;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacShowTradingRecordButton;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacStickyCrossHairButton;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacTimeframeButtons;
import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacZoomButtons;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class TacChart extends JPanel {

    private static final double KEYBOARD_PAN_PERCENTAGE = 0.1; // Scroll by 10% of the visible range
    private static final double KEYBOARD_PAGE_PAN_PERCENTAGE = 0.9; // Scroll by 90% of the visible range
    private final ChartPanel chartPanel; // Make chartPanel a field to access it in handlePanning
    private final TacChartBuilder chartBuilder;

    public TacChart(JFreeChart chart, BarSeries barSeries, TacDataTableModel tacDataTableModel, TradingRecord tradingRecord, TacChartBuilder chartBuilder) {
        super(new BorderLayout());

        this.chartBuilder = chartBuilder;

        // Create ChartPanel and override addNotify to request focus when it becomes displayable
        this.chartPanel = new ChartPanel(chart) {
            @Override
            public void addNotify() {
                super.addNotify();
                // Request focus when the panel is added to a container and becomes displayable.
                // This allows it to receive key events.
                requestFocusInWindow();
            }
        };
        chartPanel.setFocusable(true); // Essential for receiving KeyEvents

        final JToolBar toolBar = new JToolBar("Action");

        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(chartPanel), BorderLayout.CENTER);

        toolBar.add(new TacStickyCrossHairButton(new TacChartMouseHandler(chartPanel)));
        TacAutoRangeButton tacAutoRangeButton = new TacAutoRangeButton(chart);
        toolBar.add(tacAutoRangeButton);
        toolBar.add(new TacShowDataButton(new DataPanel(tacDataTableModel), this));
        toolBar.add(new TacShowTradingRecordButton(tradingRecord, this));
        toolBar.add(new TacShowBuySellSignals(chart, barSeries, tradingRecord, this));
        
        // Add timeframe buttons if multi-timeframe series is available
        new TacTimeframeButtons(chartBuilder, chartPanel, this).addToToolBar(toolBar);
        
        // Add zoom buttons
        new TacZoomButtons(chartPanel, tacAutoRangeButton).addToToolBar(toolBar);

        // Add KeyListener for keyboard scrolling
        chartPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                JFreeChart jfreeChart = chartPanel.getChart();
                if (jfreeChart == null) {
                    return;
                }
                org.jfree.chart.plot.Plot plot = jfreeChart.getPlot();
                ValueAxis domainAxisInstance = null;

                if (plot instanceof CombinedDomainXYPlot) {
                    CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) plot;
                    domainAxisInstance = combinedPlot.getDomainAxis();
                } else if (plot instanceof XYPlot) {
                    XYPlot xyPlot = (XYPlot) plot;
                    domainAxisInstance = xyPlot.getDomainAxis();
                }

                if (domainAxisInstance != null) {
                    handlePanning(e, domainAxisInstance);
                }
            }

            private void handlePanning(KeyEvent e, ValueAxis domainAxis) {
                if (!(domainAxis instanceof DateAxis)) {
                    return;
                }
                DateAxis dateAxis = (DateAxis) domainAxis;
                boolean panned = false;

                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_LEFT) {
                    dateAxis.pan(-KEYBOARD_PAN_PERCENTAGE);
                    panned = true;
                    e.consume();
                } else if (keyCode == KeyEvent.VK_RIGHT) {
                    dateAxis.pan(KEYBOARD_PAN_PERCENTAGE);
                    panned = true;
                    e.consume();
                } else if (keyCode == KeyEvent.VK_PAGE_UP) {
                    dateAxis.pan(-KEYBOARD_PAGE_PAN_PERCENTAGE);
                    panned = true;
                    e.consume();
                } else if (keyCode == KeyEvent.VK_PAGE_DOWN) {
                    dateAxis.pan(KEYBOARD_PAGE_PAN_PERCENTAGE);
                    panned = true;
                    e.consume();
                }

                if (panned && tacAutoRangeButton.isSelected()) {
                    // After panning, if Y-axes are set to auto-range, force them to readjust
                    tacAutoRangeButton.applyAutoRangeState(true);
                }
            }
        });
    }

    // Methods addTimeframeButtons and addZoomButtons have been moved to separate classes
    // TacTimeframeButtons and TacZoomButtons in the toolbar package
}