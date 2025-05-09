package de.sjwimmer.ta4jchart.chartbuilder.toolbar;

import de.sjwimmer.ta4jchart.chartbuilder.TacChart;
import de.sjwimmer.ta4jchart.chartbuilder.TacChartBuilder;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.TextAnchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class TacShowBuySellSignals extends JToggleButton implements ActionListener {

    private static final Logger log = LoggerFactory.getLogger(TacShowBuySellSignals.class);

    private final TacChart mainPanel;
    private final JFreeChart chart;
    private final TradingRecord tradingRecord;
    private final TacChartBuilder chartBuilder;

    private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 10); // Kept for profit text
    private static final Font ARROW_FONT = new Font("SansSerif", Font.PLAIN, 12); // Font for arrow characters
    private static final String UP_ARROW = "▲";
    private static final String DOWN_ARROW = "▼";

    private final List<XYAnnotation> currentAnnotations = new ArrayList<>();

    public TacShowBuySellSignals(JFreeChart chart, TradingRecord tradingRecord, TacChart mainPanel, TacChartBuilder chartBuilder) {
        super("Entry/exit Signals");
        setToolTipText("Shows/hides entry and exit signals on the chart");
        addActionListener(this);
        this.mainPanel = mainPanel;
        this.chart = chart;
        this.tradingRecord = tradingRecord;
        this.chartBuilder = chartBuilder;

        if (this.tradingRecord != null) {
            BarSeries initialBarSeries = this.chartBuilder.getCurrentBarSeries();
            if (initialBarSeries != null && !initialBarSeries.isEmpty()) {
                addBuySellSignals(this.tradingRecord, initialBarSeries, this.chart);
                setSelected(true);
            } else {
                log.warn("Initial BarSeries is null or empty. Buy/sell signals not drawn at startup.");
                setSelected(false);
            }
        } else {
            this.setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BarSeries currentBarSeries = chartBuilder.getCurrentBarSeries();
        if (currentBarSeries == null || currentBarSeries.isEmpty()) {
            log.warn("Current BarSeries is null or empty. Cannot {} buy/sell signals.", isSelected() ? "show" : "hide effectively");
            if (isSelected()) { // User tried to show signals
                removeBuySellSignals(getMainPlot(chart)); // Clear any old ones
                setSelected(false); // Force toggle off as we can't show them
            } else { // User tried to hide signals
                 removeBuySellSignals(getMainPlot(chart)); // Proceed with removal
            }
            mainPanel.revalidate();
            mainPanel.repaint();
            return;
        }

        if (isSelected()) {
            addBuySellSignals(tradingRecord, currentBarSeries, chart);
        } else {
            removeBuySellSignals(getMainPlot(chart));
        }
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private XYPlot getMainPlot(JFreeChart chartInstance) {
        if (chartInstance == null || chartInstance.getPlot() == null) return null;
        if (chartInstance.getPlot() instanceof CombinedDomainXYPlot) {
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chartInstance.getPlot();
            if (combinedPlot.getSubplots() != null && !combinedPlot.getSubplots().isEmpty()) {
                return (XYPlot) combinedPlot.getSubplots().get(0);
            }
        } else if (chartInstance.getPlot() instanceof XYPlot) {
            return (XYPlot) chartInstance.getPlot();
        }
        return null;
    }

    public void addBuySellSignals(TradingRecord record, BarSeries series, JFreeChart chartInstance) {
        final XYPlot mainPlot = getMainPlot(chartInstance);
        if (mainPlot == null) {
            log.error("Could not get main plot to draw signals.");
            return;
        }

        removeBuySellSignals(mainPlot); // Clear previous annotations

        if (record == null || series == null || series.isEmpty() || record.getPositions().isEmpty()) {
            return; // Nothing to draw
        }

        for (Position position : record.getPositions()) {
            if (position.getEntry() == null || position.getExit() == null) {
                log.trace("Skipping open or incomplete position: {}", position);
                continue;
            }

            final Trade entryTrade = position.getEntry();
            final Trade exitTrade = position.getExit();
            final int entryIndex = entryTrade.getIndex();
            final int exitIndex = exitTrade.getIndex();

            if (entryIndex < series.getBeginIndex() || entryIndex > series.getEndIndex() ||
                exitIndex < series.getBeginIndex() || exitIndex > series.getEndIndex()) {
                log.warn("Trade indices [{}(entry), {}(exit)] out of current BarSeries bounds [{}, {}]. Skipping position for display.",
                        entryIndex, exitIndex, series.getBeginIndex(), series.getEndIndex());
                continue;
            }

            final Bar entryBar = series.getBar(entryIndex);
            final Bar exitBar = series.getBar(exitIndex);

            final double entryMillis = entryBar.getEndTime().toInstant().toEpochMilli();
            final double entryPrice = entryTrade.getNetPrice().doubleValue();
            final double exitMillis = exitBar.getEndTime().toInstant().toEpochMilli();
            final double exitPrice = exitTrade.getNetPrice().doubleValue();

            Color positionColor;
            XYTextAnnotation entryArrowAnnotation;
            XYTextAnnotation exitArrowAnnotation;

            if (entryTrade.getType() == Trade.TradeType.BUY) { // BUY position
                positionColor = Color.GREEN;
                entryArrowAnnotation = new XYTextAnnotation(UP_ARROW, entryMillis, entryPrice);
                entryArrowAnnotation.setTextAnchor(TextAnchor.TOP_CENTER); // Tip of "▲" at price, body below
                exitArrowAnnotation = new XYTextAnnotation(DOWN_ARROW, exitMillis, exitPrice);
                exitArrowAnnotation.setTextAnchor(TextAnchor.BOTTOM_CENTER); // Tip of "▼" at price, body above
            } else { // SELL position
                positionColor = Color.RED;
                entryArrowAnnotation = new XYTextAnnotation(DOWN_ARROW, entryMillis, entryPrice);
                entryArrowAnnotation.setTextAnchor(TextAnchor.BOTTOM_CENTER); // Tip of "▼" at price, body above
                exitArrowAnnotation = new XYTextAnnotation(UP_ARROW, exitMillis, exitPrice);
                exitArrowAnnotation.setTextAnchor(TextAnchor.TOP_CENTER); // Tip of "▲" at price, body below
            }

            entryArrowAnnotation.setFont(ARROW_FONT);
            entryArrowAnnotation.setPaint(positionColor);
            mainPlot.addAnnotation(entryArrowAnnotation);
            currentAnnotations.add(entryArrowAnnotation);

            exitArrowAnnotation.setFont(ARROW_FONT);
            exitArrowAnnotation.setPaint(positionColor);
            mainPlot.addAnnotation(exitArrowAnnotation);
            currentAnnotations.add(exitArrowAnnotation);

            BasicStroke dottedStroke = new BasicStroke(
                    1.0f,                      // width
                    BasicStroke.CAP_BUTT,      // cap
                    BasicStroke.JOIN_MITER,    // join
                    10.0f,                     // miter limit
                    new float[] {3.0f, 3.0f},  // dash array (3px line, 3px space)
                    0.0f                       // dash phase
            );

            XYLineAnnotation connectingLine = new XYLineAnnotation(
                    entryMillis, entryPrice, exitMillis, exitPrice,
                    dottedStroke, positionColor
            );
            mainPlot.addAnnotation(connectingLine);
            currentAnnotations.add(connectingLine);

            final double profit = position.getProfit().doubleValue();
            final Color profitTextPaint = profit >= 0 ? new Color(0, 128, 0) : Color.RED; // Dark green or red

            double textX = (entryMillis + exitMillis) / 2;
            double textY = (entryPrice + exitPrice) / 2;

            XYTextAnnotation profitTextAnnotation = new XYTextAnnotation(String.format("%.2f", profit), textX, textY);
            profitTextAnnotation.setFont(LABEL_FONT);
            profitTextAnnotation.setPaint(profitTextPaint);
            profitTextAnnotation.setTextAnchor(TextAnchor.CENTER_LEFT); // Position text slightly to the right of midpoint
            // Optional: background for profit text for readability
            // profitTextAnnotation.setBackgroundPaint(new Color(220, 220, 220, 180)); 
            // profitTextAnnotation.setOutlineVisible(true);
            // profitTextAnnotation.setOutlinePaint(Color.LIGHT_GRAY);


            mainPlot.addAnnotation(profitTextAnnotation);
            currentAnnotations.add(profitTextAnnotation);
        }
    }

    public void removeBuySellSignals(XYPlot plot) {
        if (plot == null) return;
        // Create a copy to avoid ConcurrentModificationException if underlying list is modified by events
        List<XYAnnotation> toRemove = new ArrayList<>(currentAnnotations);
        currentAnnotations.clear(); 

        for (XYAnnotation annotation : toRemove) {
            plot.removeAnnotation(annotation); // Notifies chart listeners by default
        }
    }
}