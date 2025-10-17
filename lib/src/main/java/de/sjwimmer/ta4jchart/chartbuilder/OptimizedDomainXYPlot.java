package de.sjwimmer.ta4jchart.chartbuilder;

import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;

/**
 * Optimized domain XY plot that extends CombinedDomainXYPlot with custom axis location management.
 * Provides consistent axis location handling for main domain and range axes.
 */
public class OptimizedDomainXYPlot extends CombinedDomainXYPlot {
    private AxisLocation mainDomainAxisLocation;
    private AxisLocation mainRangeAxisLocation;

    /**
     * Constructs an OptimizedDomainXYPlot with the specified time axis.
     * @param timeAxis the date axis for the domain
     */
    public OptimizedDomainXYPlot(DateAxis timeAxis) {
        super(timeAxis);
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
