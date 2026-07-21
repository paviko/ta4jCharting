package de.sjwimmer.ta4jchart.chartbuilder.axis;

import org.jfree.chart.axis.NumberAxis;
import org.ta4j.core.BarSeries;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Domain axis that positions bars at consecutive integer indices instead of on a real time
 * scale. Because every bar occupies exactly one unit regardless of its timestamp, candles are
 * drawn side by side with no empty space for periods that carry no data (weekends, holidays,
 * market closes). Tick labels are rendered as the end time of the bar at each integer index,
 * looked up from the backing {@link BarSeries}, so the axis still reads as a date axis.
 */
public class BarIndexDateAxis extends NumberAxis {

    private static final ThreadLocal<SimpleDateFormat> FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    private transient BarSeries barSeries;

    public BarIndexDateAxis(String label, BarSeries barSeries) {
        super(label);
        this.barSeries = barSeries;
        setAutoRangeIncludesZero(false);
        // Bars only ever sit on integer indices, so never place a tick on a fractional position.
        setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        setNumberFormatOverride(new IndexDateFormat());
    }

    /** Points the axis at a new series (used when the chart switches timeframe). */
    public void setBarSeries(BarSeries barSeries) {
        this.barSeries = barSeries;
    }

    /** Maps an index (possibly fractional/out of range) to the end-time millis of the nearest bar. */
    public long indexToTimeMillis(double value) {
        BarSeries series = this.barSeries;
        if (series == null || series.isEmpty()) {
            return (long) value;
        }
        long index = clampToSeries(Math.round(value), series);
        return series.getBar((int) index).getEndTime().toInstant().toEpochMilli();
    }

    private String labelForIndex(double value) {
        BarSeries series = this.barSeries;
        if (series == null || series.isEmpty()) {
            return "";
        }
        // Clamp to the series so ticks in the padding slots (and the range bounds JFreeChart uses to
        // size labels for tick-spacing) still resolve to a real date instead of a blank/misleading one.
        long index = clampToSeries(Math.round(value), series);
        Date date = Date.from(series.getBar((int) index).getEndTime().toInstant());
        return FORMAT.get().format(date);
    }

    private static long clampToSeries(long index, BarSeries series) {
        if (index < series.getBeginIndex()) {
            return series.getBeginIndex();
        }
        if (index > series.getEndIndex()) {
            return series.getEndIndex();
        }
        return index;
    }

    /** Renders integer tick values as the corresponding bar date instead of a number. */
    private class IndexDateFormat extends NumberFormat {
        @Override
        public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(labelForIndex(number));
        }

        @Override
        public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(labelForIndex((double) number));
        }

        @Override
        public Number parse(String source, ParsePosition parsePosition) {
            return null;
        }
    }
}
