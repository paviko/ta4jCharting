package de.sjwimmer.ta4jchart.chartbuilder.dataset;

import org.ta4j.core.BarSeries;

public interface IDynamicDataset {
    void updateWindow(long visibleStartMillis, long visibleEndMillis);
    void setFullBarSeries(BarSeries newFullBarSeries, String newSeriesKey);
}