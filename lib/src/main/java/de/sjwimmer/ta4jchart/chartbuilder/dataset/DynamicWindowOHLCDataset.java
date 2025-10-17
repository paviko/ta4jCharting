package de.sjwimmer.ta4jchart.chartbuilder.dataset;

import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.DomainOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Date;

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
        if (this.fullBarSeries == null || this.fullBarSeries.isEmpty()) {
            clearWindowAndNotify();
        } else {
            // When series changes, the window is no longer valid.
            // A call to updateWindow with a new range is expected.
            // For safety, we can clear it or attempt an initial load based on old range if available.
            // Clearing is safest until a new range is explicitly set.
            clearWindowAndNotify(); 
        }
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

    public synchronized void updateWindow(long visibleStartMillis, long visibleEndMillis) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) {
            if (windowItemCount > 0) { // If it previously had data, clear it
                clearWindowAndNotify();
            }
            return;
        }

        int firstVisibleFullIndex = findFirstIndexOnOrAfter(visibleStartMillis);
        int lastVisibleFullIndex = findLastIndexOnOrBefore(visibleEndMillis);

        // Handle cases where the visible range is outside the data
        if (firstVisibleFullIndex == -1 && lastVisibleFullIndex == -1) { // Entirely outside
            if (visibleEndMillis < fullBarSeries.getBar(fullBarSeries.getBeginIndex()).getEndTime().toInstant().toEpochMilli()) {
                firstVisibleFullIndex = fullBarSeries.getBeginIndex(); // Load beginning
                lastVisibleFullIndex = Math.min(fullBarSeries.getEndIndex(), fullBarSeries.getBeginIndex() + bufferBars);
            } else if (visibleStartMillis > fullBarSeries.getBar(fullBarSeries.getEndIndex()).getEndTime().toInstant().toEpochMilli()) {
                lastVisibleFullIndex = fullBarSeries.getEndIndex(); // Load end
                firstVisibleFullIndex = Math.max(fullBarSeries.getBeginIndex(), fullBarSeries.getEndIndex() - bufferBars);
            } else { // In a gap or series is truly empty
                clearWindowAndNotify(); return;
            }
        } else if (firstVisibleFullIndex == -1) { // Visible range starts before data
            firstVisibleFullIndex = fullBarSeries.getBeginIndex();
        } else if (lastVisibleFullIndex == -1) { // Visible range ends after data
            lastVisibleFullIndex = fullBarSeries.getEndIndex();
        }
        
        if (firstVisibleFullIndex > lastVisibleFullIndex) { // Should not happen if logic above is correct
             // Attempt to make a small window around the more 'valid' index if one exists
            if (fullBarSeries.getBarCount() > 0) {
                int anchor = (findFirstIndexOnOrAfter(visibleStartMillis) != -1) ? findFirstIndexOnOrAfter(visibleStartMillis) : 
                             (findLastIndexOnOrBefore(visibleEndMillis) != -1) ? findLastIndexOnOrBefore(visibleEndMillis) : 
                             fullBarSeries.getBarCount()/2; // fallback to middle
                anchor = Math.max(fullBarSeries.getBeginIndex(), Math.min(anchor, fullBarSeries.getEndIndex()));
                firstVisibleFullIndex = anchor;
                lastVisibleFullIndex = anchor;
            } else {
                clearWindowAndNotify(); return;
            }
        }


        int newWindowStartFullIndex = Math.max(fullBarSeries.getBeginIndex(), firstVisibleFullIndex - bufferBars);
        int newWindowEndFullIndex = Math.min(fullBarSeries.getEndIndex(), lastVisibleFullIndex + bufferBars);

        if (newWindowStartFullIndex > newWindowEndFullIndex) { // Final check if range is invalid
            newWindowStartFullIndex = newWindowEndFullIndex = Math.max(0, Math.min(firstVisibleFullIndex, fullBarSeries.getEndIndex())); // single point
            if (fullBarSeries.isEmpty()) {clearWindowAndNotify(); return;}
        }
        
        int newWindowItemCount = newWindowEndFullIndex - newWindowStartFullIndex + 1;

        if (newWindowItemCount <= 0 && !fullBarSeries.isEmpty()) { // Should not happen if series has data
             log.warn("Calculated window count is {} for non-empty series. Forcing a single point window at start.", newWindowItemCount);
             newWindowStartFullIndex = fullBarSeries.getBeginIndex();
             newWindowEndFullIndex = fullBarSeries.getBeginIndex();
             newWindowItemCount = 1;
        } else if (newWindowItemCount <= 0 && fullBarSeries.isEmpty()){
            clearWindowAndNotify(); return;
        }


        // Avoid unnecessary reloads if the window content is identical
        if (newWindowStartFullIndex == this.windowStartFullIndex && newWindowItemCount == this.windowItemCount) {
            return;
        }
        
        log.debug("Updating window: FullIdx [{}, {}], Items: {}, VisMs [{}, {}]",
                newWindowStartFullIndex, newWindowEndFullIndex, newWindowItemCount,
                new Date(visibleStartMillis), new Date(visibleEndMillis));

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

    private int findFirstIndexOnOrAfter(long timeMillis) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) return -1;
        int begin = fullBarSeries.getBeginIndex();
        int end = fullBarSeries.getEndIndex();
        if (timeMillis > fullBarSeries.getBar(end).getEndTime().toInstant().toEpochMilli()) return -1;
        if (timeMillis <= fullBarSeries.getBar(begin).getEndTime().toInstant().toEpochMilli()) return begin;

        int low = begin, high = end, result = end + 1; // result if no bar is >= timeMillis
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (fullBarSeries.getBar(mid).getEndTime().toInstant().toEpochMilli() >= timeMillis) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return (result > end) ? -1 : result;
    }

    private int findLastIndexOnOrBefore(long timeMillis) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) return -1;
        int begin = fullBarSeries.getBeginIndex();
        int end = fullBarSeries.getEndIndex();
        if (timeMillis < fullBarSeries.getBar(begin).getEndTime().toInstant().toEpochMilli()) return -1;
        if (timeMillis >= fullBarSeries.getBar(end).getEndTime().toInstant().toEpochMilli()) return end;
        
        int low = begin, high = end, result = begin -1; // result if no bar is <= timeMillis
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (fullBarSeries.getBar(mid).getEndTime().toInstant().toEpochMilli() <= timeMillis) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return (result < begin) ? -1 : result;
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

    @Override public Number getX(int series, int item) { checkSeriesItem(series, item); return dates[item].getTime(); }
    @Override public double getXValue(int series, int item) { checkSeriesItem(series, item); return dates[item].getTime(); }
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