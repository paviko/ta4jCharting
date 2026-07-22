package de.sjwimmer.ta4jchart.chartbuilder.dataset;

import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.DomainOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Sliding-window indicator dataset whose X coordinate is the bar's index in the full series rather
 * than its timestamp, so indicator lines stay aligned with the gap-free, index-based candles.
 */
public class DynamicWindowXYDataset extends AbstractXYDataset implements IDynamicDataset {
    private static final Logger log = LoggerFactory.getLogger(DynamicWindowXYDataset.class);

    private BarSeries fullBarSeries;
    private final Indicator<Num> indicator;
    private String seriesKey;

    private double[] yValues;

    private int windowStartFullIndex = -1;
    private int windowItemCount = 0;

    private final int bufferBars;

    public DynamicWindowXYDataset(BarSeries initialFullBarSeries, Indicator<Num> indicator, String seriesKey, int bufferBars) {
        this.indicator = indicator;
        this.seriesKey = seriesKey;
        this.bufferBars = Math.max(0, bufferBars);
        setFullBarSeries(initialFullBarSeries, seriesKey);
    }

    @Override
    public synchronized void setFullBarSeries(BarSeries newFullBarSeries, String newSeriesKey) {
        this.fullBarSeries = newFullBarSeries;
        this.seriesKey = newSeriesKey;
        if (this.fullBarSeries != null && !this.fullBarSeries.isEmpty()
                && this.indicator.getBarSeries() != this.fullBarSeries) {
            log.warn("Indicator's bar series does not match the new bar series for {}", seriesKey);
        }
        clearWindowAndNotify();
    }

    private void clearWindowAndNotify() {
        clearWindowInternal();
        fireDatasetChanged();
    }

    private void clearWindowInternal() {
        this.yValues = new double[0];
        this.windowStartFullIndex = -1;
        this.windowItemCount = 0;
    }

    /**
     * Rebuilds the visible window. The bounds are domain-axis values, which for the index-based
     * domain axis are bar indices (not timestamps).
     */
    @Override
    public synchronized void updateWindow(long lowerIndexBound, long upperIndexBound) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) {
            if (windowItemCount > 0) {
                clearWindowAndNotify();
            }
            return;
        }

        int begin = fullBarSeries.getBeginIndex();
        int end = fullBarSeries.getEndIndex();

        int firstVisible = clampIndex(lowerIndexBound, begin, end);
        int lastVisible = clampIndex(upperIndexBound, begin, end);
        if (firstVisible > lastVisible) {
            int tmp = firstVisible;
            firstVisible = lastVisible;
            lastVisible = tmp;
        }

        int newWindowStartFullIndex = Math.max(begin, firstVisible - bufferBars);
        int newWindowEndFullIndex = Math.min(end, lastVisible + bufferBars);
        int newWindowItemCount = newWindowEndFullIndex - newWindowStartFullIndex + 1;

        if (newWindowItemCount <= 0) {
            clearWindowAndNotify();
            return;
        }

        if (newWindowStartFullIndex == this.windowStartFullIndex && newWindowItemCount == this.windowItemCount) {
            return;
        }

        log.debug("Updating XY window: FullIdx [{}, {}], Items: {}, Key: {}",
                newWindowStartFullIndex, newWindowEndFullIndex, newWindowItemCount, this.seriesKey);

        this.yValues = new double[newWindowItemCount];

        // The indicator is computed on its own bar series, which may differ from the chart's current
        // series after a timeframe switch (e.g. an M15 channel shown on a D1/M1 chart). When they
        // differ, indexing the indicator with the chart's index reads an unrelated bar and the line
        // lands far from the candles, so align by time instead: each candle shows the indicator value
        // that was current at the candle's close.
        BarSeries indicatorSeries = indicator.getBarSeries();
        boolean sameSeries = (indicatorSeries == fullBarSeries);

        for (int i = 0; i < newWindowItemCount; i++) {
            int fullSeriesIndex = newWindowStartFullIndex + i;
            if (sameSeries) {
                this.yValues[i] = indicator.getValue(fullSeriesIndex).doubleValue();
            } else {
                long timeMillis = fullBarSeries.getBar(fullSeriesIndex).getEndTime().toInstant().toEpochMilli();
                int indicatorIndex = asOfIndexForTime(indicatorSeries, timeMillis);
                this.yValues[i] = (indicatorIndex >= 0)
                        ? indicator.getValue(indicatorIndex).doubleValue()
                        : Double.NaN;
            }
        }

        this.windowStartFullIndex = newWindowStartFullIndex;
        this.windowItemCount = newWindowItemCount;
        fireDatasetChanged();
    }

    private static int clampIndex(long value, int begin, int end) {
        if (value < begin) return begin;
        if (value > end) return end;
        return (int) value;
    }

    /**
     * Returns the index of the last bar in {@code series} whose end time is at or before
     * {@code timeMillis} (an "as-of" lookup), or -1 if the time precedes the series' first bar or
     * the series is empty. Used to align an indicator computed on one timeframe with candles on
     * another so each candle shows the indicator value that was current at its close.
     */
    private static int asOfIndexForTime(BarSeries series, long timeMillis) {
        if (series == null || series.isEmpty()) {
            return -1;
        }
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        if (timeMillis < series.getBar(begin).getEndTime().toInstant().toEpochMilli()) {
            return -1;
        }
        int low = begin;
        int high = end;
        while (low < high) {
            int mid = low + (high - low + 1) / 2; // upper mid to converge on the last matching bar
            long midTime = series.getBar(mid).getEndTime().toInstant().toEpochMilli();
            if (midTime <= timeMillis) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    @Override public DomainOrder getDomainOrder() { return DomainOrder.ASCENDING; }
    @Override public int getItemCount(int series) { return (series == 0) ? windowItemCount : 0; }
    @Override public int getSeriesCount() { return 1; }
    @Override public Comparable<?> getSeriesKey(int series) { return (series == 0) ? this.seriesKey : null; }

    private void checkSeriesItem(int series, int item) {
        if (series != 0) throw new IllegalArgumentException("Invalid series index: " + series);
        if (item < 0 || item >= windowItemCount) throw new IndexOutOfBoundsException("Invalid item index: " + item + " for window size " + windowItemCount);
    }

    @Override public Number getX(int series, int item) { checkSeriesItem(series, item); return windowStartFullIndex + item; }
    @Override public double getXValue(int series, int item) { checkSeriesItem(series, item); return windowStartFullIndex + item; }
    @Override public Number getY(int series, int item) { checkSeriesItem(series, item); return yValues[item]; }
    @Override public double getYValue(int series, int item) { checkSeriesItem(series, item); return yValues[item]; }
}
