package de.sjwimmer.ta4jchart.chartbuilder.dataset;

import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.DomainOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Date;

/**
 * Sliding-window OHLC dataset whose X coordinate is the bar's index in the full series rather than
 * its timestamp. Placing bars at consecutive integer positions keeps candles evenly spaced with no
 * gaps for periods that carry no data (weekends, holidays, market closes). Timestamps are still kept
 * for the OHLC tooltip/date lookup via {@link #indexToTimeMillis(double)}.
 */
public class DynamicWindowOHLCDataset extends AbstractXYDataset implements OHLCDataset, IDynamicDataset {

    private static final Logger log = LoggerFactory.getLogger(DynamicWindowOHLCDataset.class);

    private BarSeries fullBarSeries; // Reference to the complete BarSeries for the current timeframe
    private String seriesKey;

    // Windowed data arrays
    private Date[] dates;
    private double[] highs;
    private double[] lows;
    private double[] opens;
    private double[] closes;
    private double[] volumes;

    private int windowStartFullIndex = -1; // Start index of the current window in the fullBarSeries
    private int windowItemCount = 0;

    private final int bufferBars; // Number of additional bars to load on each side of the visible range

    public DynamicWindowOHLCDataset(BarSeries initialFullBarSeries, String seriesKey, int bufferBars) {
        if (initialFullBarSeries == null) {
            // Allow null for initial setup, but updateWindow will clear if still null
            log.warn("DynamicWindowOHLCDataset initialized with null BarSeries. Ensure it's set before use.");
            this.fullBarSeries = null;
        } else {
            this.fullBarSeries = initialFullBarSeries;
        }
        this.seriesKey = seriesKey;
        this.bufferBars = Math.max(0, bufferBars);
        clearWindowAndNotify(); // Initialize with empty arrays and notify
    }

    public synchronized void setFullBarSeries(BarSeries newFullBarSeries, String newSeriesKey) {
        this.fullBarSeries = newFullBarSeries;
        this.seriesKey = newSeriesKey;
        // When the series changes the current window is no longer valid; a new range is expected.
        clearWindowAndNotify();
    }

    private void clearWindowAndNotify() {
        clearWindowInternal();
        fireDatasetChanged();
    }

    private void clearWindowInternal() {
        this.dates = new Date[0];
        this.opens = new double[0];
        this.highs = new double[0];
        this.lows = new double[0];
        this.closes = new double[0];
        this.volumes = new double[0];
        this.windowStartFullIndex = -1;
        this.windowItemCount = 0;
    }

    /**
     * Rebuilds the visible window. The bounds are domain-axis values, which for the index-based
     * domain axis are bar indices (not timestamps).
     */
    public synchronized void updateWindow(long lowerIndexBound, long upperIndexBound) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) {
            if (windowItemCount > 0) { // If it previously had data, clear it
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

        // Avoid unnecessary reloads if the window content is identical
        if (newWindowStartFullIndex == this.windowStartFullIndex && newWindowItemCount == this.windowItemCount) {
            return;
        }

        log.debug("Updating window: FullIdx [{}, {}], Items: {}", newWindowStartFullIndex, newWindowEndFullIndex, newWindowItemCount);

        this.dates = new Date[newWindowItemCount];
        this.opens = new double[newWindowItemCount];
        this.highs = new double[newWindowItemCount];
        this.lows = new double[newWindowItemCount];
        this.closes = new double[newWindowItemCount];
        this.volumes = new double[newWindowItemCount];

        for (int i = 0; i < newWindowItemCount; i++) {
            int fullSeriesIndex = newWindowStartFullIndex + i;
            Bar bar = fullBarSeries.getBar(fullSeriesIndex);
            this.dates[i] = Date.from(bar.getEndTime().toInstant());
            this.opens[i] = bar.getOpenPrice().doubleValue();
            this.highs[i] = bar.getHighPrice().doubleValue();
            this.lows[i] = bar.getLowPrice().doubleValue();
            this.closes[i] = bar.getClosePrice().doubleValue();
            this.volumes[i] = bar.getVolume().doubleValue();
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

    /** Maps a domain value (bar index) to the end-time millis of the nearest bar in the full series. */
    public synchronized long indexToTimeMillis(double domainValue) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) {
            return (long) domainValue;
        }
        int idx = clampIndex(Math.round(domainValue), fullBarSeries.getBeginIndex(), fullBarSeries.getEndIndex());
        return fullBarSeries.getBar(idx).getEndTime().toInstant().toEpochMilli();
    }

    // --- XYDataset and OHLCDataset implementations ---
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
    @Override public Number getY(int series, int item) { checkSeriesItem(series, item); return closes[item]; } // Default Y for XYDataset
    @Override public double getYValue(int series, int item) { checkSeriesItem(series, item); return closes[item]; }
    @Override public Number getHigh(int series, int item) { checkSeriesItem(series, item); return highs[item]; }
    @Override public double getHighValue(int series, int item) { checkSeriesItem(series, item); return highs[item]; }
    @Override public Number getLow(int series, int item) { checkSeriesItem(series, item); return lows[item]; }
    @Override public double getLowValue(int series, int item) { checkSeriesItem(series, item); return lows[item]; }
    @Override public Number getOpen(int series, int item) { checkSeriesItem(series, item); return opens[item]; }
    @Override public double getOpenValue(int series, int item) { checkSeriesItem(series, item); return opens[item]; }
    @Override public Number getClose(int series, int item) { checkSeriesItem(series, item); return closes[item]; }
    @Override public double getCloseValue(int series, int item) { checkSeriesItem(series, item); return closes[item]; }
    @Override public Number getVolume(int series, int item) { checkSeriesItem(series, item); return volumes[item]; }
    @Override public double getVolumeValue(int series, int item) { checkSeriesItem(series, item); return volumes[item]; }
}
