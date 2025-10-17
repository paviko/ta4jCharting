package de.sjwimmer.ta4jchart.chartbuilder;

import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

/**
 * Optimized XY line and shape renderer that extends XYLineAndShapeRenderer with custom tooltip handling.
 * Provides conditional tooltip generation based on manual tooltip flag.
 */
public class OptmizedXYLineAndShapeRenderer extends XYLineAndShapeRenderer {
    private boolean manualTooltip;

    /**
     * Constructs an OptmizedXYLineAndShapeRenderer with specified line and shape visibility.
     * @param lines whether to show lines
     * @param shapes whether to show shapes
     */
    public OptmizedXYLineAndShapeRenderer(boolean lines, boolean shapes) {
        super(lines, shapes);
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
