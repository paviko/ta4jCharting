package de.sjwimmer.ta4jchart.chartbuilder;

import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;

/**
 * Optimized XY plot that extends XYPlot with custom axis location management.
 * Provides consistent axis location handling for main domain and range axes.
 */
public class OptimizedXYPlot extends XYPlot {
    private AxisLocation mainDomainAxisLocation;
    private AxisLocation mainRangeAxisLocation;

    /**
     * Constructs an OptimizedXYPlot with the specified dataset, axes, and renderer.
     * @param dataset the dataset for the plot
     * @param domainAxis the domain axis
     * @param rangeAxis the range axis
     * @param renderer the item renderer
     */
    public OptimizedXYPlot(XYDataset dataset, ValueAxis domainAxis, ValueAxis rangeAxis, XYItemRenderer renderer) {
        super(dataset, domainAxis, rangeAxis, renderer);
        mainDomainAxisLocation = AxisLocation.BOTTOM_OR_LEFT;
        mainRangeAxisLocation = AxisLocation.BOTTOM_OR_LEFT;
    }

    /**
     * Gets the location of the domain axis.
     * @return the domain axis location
     */
    @Override
    public AxisLocation getDomainAxisLocation() {
        return mainDomainAxisLocation;
    }

    /**
     * Sets the location of the domain axis at the specified index.
     * @param index the axis index
     * @param location the axis location
     * @param notify whether to notify listeners
     */
    @Override
    public void setDomainAxisLocation(int index, AxisLocation location, boolean notify) {
        if (index == 0) {
            mainDomainAxisLocation = location;
        }
        super.setDomainAxisLocation(index, location, notify);
    }

    /**
     * Gets the location of the range axis.
     * @return the range axis location
     */
    @Override
    public AxisLocation getRangeAxisLocation() {
        return mainRangeAxisLocation;
    }

    /**
     * Sets the location of the range axis at the specified index.
     * @param index the axis index
     * @param location the axis location
     * @param notify whether to notify listeners
     */
    @Override
    public void setRangeAxisLocation(int index, AxisLocation location, boolean notify) {
        if (index == 0) {
            mainRangeAxisLocation = location;
        }
        super.setRangeAxisLocation(index, location, notify);
    }
}
