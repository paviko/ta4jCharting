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
}
