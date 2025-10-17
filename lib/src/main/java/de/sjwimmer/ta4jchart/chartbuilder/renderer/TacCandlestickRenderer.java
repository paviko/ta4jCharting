package de.sjwimmer.ta4jchart.chartbuilder.renderer;

import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.OHLCDataset;

import java.awt.*;

/**
 * TA4J candlestick renderer that extends CandlestickRenderer with custom item painting and tooltip handling.
 * Paints candlesticks based on open/close values and provides conditional tooltip generation.
 */
public class TacCandlestickRenderer extends CandlestickRenderer {
    private boolean manualTooltip;

    /**
     * Gets the paint for the candlestick item based on open and close values.
     * Uses down paint for bearish candles (open > close) and up paint for bullish candles (open <= close).
     * @param row the row index
     * @param column the column index
     * @return the paint for the item
     */
    @Override
    public Paint getItemPaint(int row, int column) {
        OHLCDataset dataset = (OHLCDataset) getPlot().getDataset();
        double openValue = dataset.getOpenValue(row, column);
        double close = dataset.getCloseValue(row, column);
        if(openValue > close) {
            return getDownPaint();
        } else {
            return getUpPaint();
        }
    }

    /**
     * Gets the tooltip generator for the specified series and item.
     * Returns manual tooltip generator if set, otherwise returns default tooltip generator.
     * @param series the series index
     * @param item the item index
     * @return the tooltip generator
     */
    @Override
    public XYToolTipGenerator getToolTipGenerator(int series, int item) {
        if (manualTooltip) {
            return super.getToolTipGenerator(series, item);
        }
        return this.getDefaultToolTipGenerator();
    }

    /**
     * Sets the tooltip generator for the specified series and enables manual tooltip mode.
     * @param series the series index
     * @param generator the tooltip generator
     */
    @Override
    public void setSeriesToolTipGenerator(int series, XYToolTipGenerator generator) {
        manualTooltip = true;
        super.setSeriesToolTipGenerator(series, generator);
    }
}
