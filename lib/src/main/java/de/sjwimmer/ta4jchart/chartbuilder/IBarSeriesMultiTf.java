package de.sjwimmer.ta4jchart.chartbuilder;

import org.ta4j.core.BarSeries;
import com.limemojito.trading.model.bar.Bar.Period;

/**
 * Interface for a bar series that can be accessed by period.
 */
public interface IBarSeriesMultiTf {
    /**
     * Returns the bar series for the given period.
     *
     * @param period the period to get the bar series for
     * @return the bar series for the given period
     */
    BarSeries at(Period period);

    /**
     * Returns the period of this bar series.
     *
     * @return the period
     */
    Period getPeriod();

    /**
     * Returns the parent period of this bar series.
     *
     * @return the parent period
     */
    Period getParentPeriod();
    
    /**
     * Returns the parent bar series of this bar series.
     *
     * @return the parent bar series
     */
    IBarSeriesMultiTf getParent();

    /**
     * Returns the parent to index mapping for the given index.
     * Maps parent index -> first child index within parent's time range
     * 
     * @param index the index to get the parent to index mapping for
     * @return the parent to index mapping
     */
    int getParentToIndex(int index);

    /**
     * Returns the index to parent mapping for the given index.
     * Maps child index -> parent index containing child's time range
     * 
     * @param index the index to get the index to parent mapping for
     * @return the index to parent mapping
     */
    int getIndexToParent(int index);
}
