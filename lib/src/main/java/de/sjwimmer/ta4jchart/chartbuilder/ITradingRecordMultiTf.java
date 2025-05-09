package de.sjwimmer.ta4jchart.chartbuilder;

import com.limemojito.trading.model.bar.Bar.Period;

/**
 * Interface for a trading record that can be accessed by period.
 */
public interface ITradingRecordMultiTf {
    void setTargetPeriod(Period targetPeriod);
}
