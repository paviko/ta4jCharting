package de.sjwimmer.ta4jchart.chartbuilder.dataset;

import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.DomainOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.Date;

public class DynamicWindowXYDataset extends AbstractXYDataset implements IDynamicDataset {
    private static final Logger log = LoggerFactory.getLogger(DynamicWindowXYDataset.class);

    private BarSeries fullBarSeries;
    private final Indicator<Num> indicator;
    private String seriesKey;

    private long[] xValues;
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
        if (this.fullBarSeries == null || this.fullBarSeries.isEmpty() || this.indicator.getBarSeries() != this.fullBarSeries) {
             if (this.indicator.getBarSeries() != newFullBarSeries) {
                log.warn("Indicator's bar series does not match the new bar series for {}", seriesKey);
             }
            clearWindowAndNotify();
        } else {
            clearWindowAndNotify(); 
        }
    }
    
    private void clearWindowAndNotify() {
        clearWindowInternal();
        fireDatasetChanged();
    }

    private void clearWindowInternal() {
        this.xValues = new long[0];
        this.yValues = new double[0];
        this.windowStartFullIndex = -1;
        this.windowItemCount = 0;
    }

    @Override
    public synchronized void updateWindow(long visibleStartMillis, long visibleEndMillis) {
        if (fullBarSeries == null || fullBarSeries.isEmpty()) {
            if (windowItemCount > 0) {
                clearWindowAndNotify();
            }
            return;
        }

        int firstVisibleFullIndex = findFirstIndexOnOrAfter(visibleStartMillis);
        int lastVisibleFullIndex = findLastIndexOnOrBefore(visibleEndMillis);

        if (firstVisibleFullIndex == -1 && lastVisibleFullIndex == -1) {
            if (visibleEndMillis < fullBarSeries.getBar(fullBarSeries.getBeginIndex()).getEndTime().toInstant().toEpochMilli()) {
                firstVisibleFullIndex = fullBarSeries.getBeginIndex();
                lastVisibleFullIndex = Math.min(fullBarSeries.getEndIndex(), fullBarSeries.getBeginIndex() + bufferBars);
            } else if (visibleStartMillis > fullBarSeries.getBar(fullBarSeries.getEndIndex()).getEndTime().toInstant().toEpochMilli()) {
                lastVisibleFullIndex = fullBarSeries.getEndIndex();
                firstVisibleFullIndex = Math.max(fullBarSeries.getBeginIndex(), fullBarSeries.getEndIndex() - bufferBars);
            } else {
                clearWindowAndNotify(); return;
            }
        } else if (firstVisibleFullIndex == -1) {
            firstVisibleFullIndex = fullBarSeries.getBeginIndex();
        } else if (lastVisibleFullIndex == -1) {
            lastVisibleFullIndex = fullBarSeries.getEndIndex();
        }
        
        if (firstVisibleFullIndex > lastVisibleFullIndex) {
            if (fullBarSeries.getBarCount() > 0) {
                int anchor = (findFirstIndexOnOrAfter(visibleStartMillis) != -1) ? findFirstIndexOnOrAfter(visibleStartMillis) : 
                             (findLastIndexOnOrBefore(visibleEndMillis) != -1) ? findLastIndexOnOrBefore(visibleEndMillis) : 
                             fullBarSeries.getBarCount()/2;
                anchor = Math.max(fullBarSeries.getBeginIndex(), Math.min(anchor, fullBarSeries.getEndIndex()));
                firstVisibleFullIndex = anchor;
                lastVisibleFullIndex = anchor;
            } else {
                clearWindowAndNotify(); return;
            }
        }

        int newWindowStartFullIndex = Math.max(fullBarSeries.getBeginIndex(), firstVisibleFullIndex - bufferBars);
        int newWindowEndFullIndex = Math.min(fullBarSeries.getEndIndex(), lastVisibleFullIndex + bufferBars);

        if (newWindowStartFullIndex > newWindowEndFullIndex) {
            newWindowStartFullIndex = newWindowEndFullIndex = Math.max(0, Math.min(firstVisibleFullIndex, fullBarSeries.getEndIndex()));
            if (fullBarSeries.isEmpty()) {clearWindowAndNotify(); return;}
        }
        
        int newWindowItemCount = newWindowEndFullIndex - newWindowStartFullIndex + 1;

        if (newWindowItemCount <= 0 && !fullBarSeries.isEmpty()) {
             newWindowStartFullIndex = fullBarSeries.getBeginIndex();
             newWindowEndFullIndex = fullBarSeries.getBeginIndex();
             newWindowItemCount = 1;
        } else if (newWindowItemCount <= 0 && fullBarSeries.isEmpty()){
            clearWindowAndNotify(); return;
        }

        if (newWindowStartFullIndex == this.windowStartFullIndex && newWindowItemCount == this.windowItemCount) {
            return;
        }
        
        log.debug("Updating XY window: FullIdx [{}, {}], Items: {}, Key: {}",
                newWindowStartFullIndex, newWindowEndFullIndex, newWindowItemCount, this.seriesKey);

        this.xValues = new long[newWindowItemCount];
        this.yValues = new double[newWindowItemCount];

        for (int i = 0; i < newWindowItemCount; i++) {
            int fullSeriesIndex = newWindowStartFullIndex + i;
            Bar bar = fullBarSeries.getBar(fullSeriesIndex);
            Num value = indicator.getValue(fullSeriesIndex);
            this.xValues[i] = bar.getEndTime().toInstant().toEpochMilli();
            this.yValues[i] = value.doubleValue();
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

        int low = begin, high = end, result = end + 1;
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
        
        int low = begin, high = end, result = begin -1;
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

    @Override public DomainOrder getDomainOrder() { return DomainOrder.ASCENDING; }
    @Override public int getItemCount(int series) { return (series == 0) ? windowItemCount : 0; }
    @Override public int getSeriesCount() { return 1; }
    @Override public Comparable<?> getSeriesKey(int series) { return (series == 0) ? this.seriesKey : null; }

    private void checkSeriesItem(int series, int item) {
        if (series != 0) throw new IllegalArgumentException("Invalid series index: " + series);
        if (item < 0 || item >= windowItemCount) throw new IndexOutOfBoundsException("Invalid item index: " + item + " for window size " + windowItemCount);
    }

    @Override public Number getX(int series, int item) { checkSeriesItem(series, item); return xValues[item]; }
    @Override public double getXValue(int series, int item) { checkSeriesItem(series, item); return xValues[item]; }
    @Override public Number getY(int series, int item) { checkSeriesItem(series, item); return yValues[item]; }
    @Override public double getYValue(int series, int item) { checkSeriesItem(series, item); return yValues[item]; }
}