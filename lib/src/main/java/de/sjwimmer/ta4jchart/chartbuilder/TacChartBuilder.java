package de.sjwimmer.ta4jchart.chartbuilder;

import com.limemojito.trading.model.bar.Bar.Period;
import de.sjwimmer.ta4jchart.chartbuilder.converter.*;
import de.sjwimmer.ta4jchart.chartbuilder.data.TacDataTableModel;
import de.sjwimmer.ta4jchart.chartbuilder.renderer.*;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TacChartBuilder implements IChartBuilderAdapter{
	private static final Logger log = LoggerFactory.getLogger(TacChartBuilder.class);

	private final TacChartTheme theme;
	private BarSeries barSeries; // This will now be the BarSeries for the *current* timeframe
	private final IBarSeriesMultiTf multiTfBarSeries; // Store the original multi-TF series
	private TradingRecord tradingRecord;
	private final BarSeriesConverter barSeriesConverter;
	private final IndicatorToTimeSeriesConverter indicatorToTimeSeriesConverter;
	private final IndicatorToBarDataConverter indicatorToBarDataConverter;
	private final JFreeChart chart;

	private final TacDataTableModel dataTableModel = new TacDataTableModel();
    private final List<IndicatorConfiguration.Builder<?>> indicatorConfigBuilders = new ArrayList<>(); // Store builders

	private int overlayIds = 2; // 0 = ohlcv data, 1 = volume data

	private static final int BARS_PER_100PX = 8;

	public static TacChartBuilder of(BarSeries barSeries) {
		return of(barSeries, Theme.LIGHT);
	}

	public static TacChartBuilder of(BarSeries barSeries, Theme theme) {
		return new TacChartBuilder(barSeries, theme);
	}

	private TacChartBuilder(BarSeries barSeries, Theme theme) {
		this(barSeries, new BarSeriesConverterImpl(), new IndicatorToTimeSeriesConverterImpl(), new IndicatorToBarDataConverterImpl(), theme);
	}
	
	private TacChartBuilder(BarSeries barSeries, BarSeriesConverter barseriesPlotter, IndicatorToTimeSeriesConverter indicatorConverter, IndicatorToBarDataConverter indicatorToBarDataConverter, Theme theme) {
		if(theme == Theme.DARK) {
			this.theme = new DarkTacChartTheme();
		} else {
			this.theme = new LightTacChartTheme();
		}
		this.barSeriesConverter = barseriesPlotter;
		this.indicatorToTimeSeriesConverter = indicatorConverter;
		this.indicatorToBarDataConverter = indicatorToBarDataConverter;
		this.barSeries = barSeries;
		if (barSeries instanceof IBarSeriesMultiTf) {
			this.multiTfBarSeries = (IBarSeriesMultiTf) barSeries;
		} else {
			this.multiTfBarSeries = null;
		}
		this.chart = createCandlestickChart(this.barSeries);
	}

	/**
	 * Builds the chart
	 * @return a JPanel holding all ta4j-charting elements
	 */
	public TacChart build() {
        // Ensure all indicators are added before building
        for (IndicatorConfiguration.Builder<?> builder : indicatorConfigBuilders) {
            addIndicatorToPlot(builder.build(), this.barSeries);
        }
        // Initial zoom is now handled by TacChart.addNotify() calling setInitialChartViewport
		return new TacChart(chart, barSeries, dataTableModel, tradingRecord, this);
	}

    public void setInitialChartViewport(JFreeChart chartToConfigure, int chartPanelWidth) {
        if (this.barSeries == null || this.barSeries.isEmpty() || chartToConfigure == null) {
            return;
        }

        org.jfree.chart.plot.Plot plot = chartToConfigure.getPlot();
        if (!(plot instanceof CombinedDomainXYPlot)) {
            return;
        }

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) plot;
        ValueAxis domainAxis = combinedPlot.getDomainAxis();

        if (!(domainAxis instanceof DateAxis)) {
            return;
        }
        DateAxis dateAxis = (DateAxis) domainAxis;

        // 1. Estimate average bar duration
        long avgBarDurationMillis = 5 * 60 * 1000; // Default 5 minutes
        if (this.barSeries instanceof IBarSeriesMultiTf) {
            Period period = ((IBarSeriesMultiTf) this.barSeries).getPeriod();
            if (period != null && period.getDuration() != null) {
                avgBarDurationMillis = period.getDuration().toMillis();
            }
        } else if (this.barSeries.getBarCount() > 1) {
            int lastIdx = this.barSeries.getEndIndex();
            int prevIdx = Math.max(this.barSeries.getBeginIndex(), lastIdx - 1);
            if (lastIdx > prevIdx) { // Make sure we have at least two distinct bars
                avgBarDurationMillis = this.barSeries.getBar(lastIdx).getEndTime().toInstant().toEpochMilli() -
                                       this.barSeries.getBar(prevIdx).getEndTime().toInstant().toEpochMilli();
            }
        }
        if (avgBarDurationMillis <= 0) { // Sanity check
            avgBarDurationMillis = 5 * 60 * 1000;
        }

        // 2. Determine the number of bars to display
        int barsToDisplay = 100; // Default
        if (chartPanelWidth > 0) {
            barsToDisplay = (int) (((double) chartPanelWidth / 100.0) * BARS_PER_100PX);
            if (barsToDisplay < 10) barsToDisplay = 10; // Minimum of 10 bars
        }
        barsToDisplay = Math.min(barsToDisplay, this.barSeries.getBarCount()); // Cannot display more than available

        if (barsToDisplay <= 0) { // Not enough bars or invalid width
             dateAxis.setAutoRange(true); // Fallback
             return;
        }
        
        // 3. Calculate date range
        int endIndex = this.barSeries.getEndIndex();
        
        // Upper bound: end time of the last bar + one average bar duration (for padding)
        long lastBarActualEndTimeMillis = this.barSeries.getBar(endIndex).getEndTime().toInstant().toEpochMilli();
        long upperBoundTimeMillis = lastBarActualEndTimeMillis + avgBarDurationMillis;

        // Lower bound:
        // Find the (endIndex - barsToDisplay + 1)-th bar. The range should start before this bar.
        int firstVisibleBarIndex = Math.max(this.barSeries.getBeginIndex(), endIndex - barsToDisplay + 1);
        long lowerBoundTimeMillis;

        if (firstVisibleBarIndex > this.barSeries.getBeginIndex()) {
            // Use the end time of the bar *before* the first visible bar as the lower bound.
            lowerBoundTimeMillis = this.barSeries.getBar(firstVisibleBarIndex - 1).getEndTime().toInstant().toEpochMilli();
        } else {
            // The first visible bar is the first bar in the series.
            // Start the range one bar duration *before* its end time (approximates its begin time).
            lowerBoundTimeMillis = this.barSeries.getBar(firstVisibleBarIndex).getEndTime().toInstant().toEpochMilli() - avgBarDurationMillis;
        }
        
        if (lowerBoundTimeMillis < upperBoundTimeMillis) {
            dateAxis.setRange(new Date(lowerBoundTimeMillis), new Date(upperBoundTimeMillis));
            dateAxis.setAutoRange(false); // CRUCIAL
        } else {
            dateAxis.setAutoRange(true); // Fallback if calculation is off
        }
    }

    // Method to be called by TacChart for timeframe switching
    public JFreeChart switchTimeframe(Period newTimeframe) {
        if (this.multiTfBarSeries == null) {
            log.error("Cannot switch timeframe: IBarSeriesMultiTf not provided.");
            return this.chart;
        }

		if (this.tradingRecord instanceof ITradingRecordMultiTf) {
			((ITradingRecordMultiTf) this.tradingRecord).setTargetPeriod(newTimeframe);
		}
		
        Date oldLowerBound = null;
        Date oldUpperBound = null;
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) this.chart.getPlot();
        ValueAxis domainAxis = combinedPlot.getDomainAxis();
        if (domainAxis instanceof DateAxis) {
            DateAxis dateAxis = (DateAxis) domainAxis;
            oldLowerBound = dateAxis.getMinimumDate();
            oldUpperBound = dateAxis.getMaximumDate();
        }

        this.barSeries = this.multiTfBarSeries.at(newTimeframe);
        if (this.barSeries == null || this.barSeries.isEmpty()) {
            log.error("Failed to get BarSeries for timeframe: {} or series is empty.", newTimeframe.name());
            // Optionally, revert to a default or show an error message on chart
            return this.chart;
        }

        // 1. Update main candlestick plot
        XYPlot candlestickPlot = (XYPlot) combinedPlot.getSubplots().get(0);
        DefaultHighLowDataset newCandlestickData = this.barSeriesConverter.convert(this.barSeries);
        candlestickPlot.setDataset(0, newCandlestickData); // Dataset index 0 is for candlesticks

        // 2. Clear old data table model entries (except potentially date/close from new series)
        this.dataTableModel.clearAllEntries();
        this.dataTableModel.addEntries(newCandlestickData); // Add new OHLC data

        // 3. Clear existing indicator datasets and renderers from all plots
        // Reset overlayIds for the main plot
        this.overlayIds = 2; // Candlestick is 0, Volume (part of candlestick) is effectively 1
                             // Or 1 if you don't count volume as separate overlayId from candlestick renderer

        // Clear overlays from candlestick plot (from index overlayIds upwards)
        for (int i = candlestickPlot.getDatasetCount() - 1; i >= overlayIds; i--) {
            candlestickPlot.setDataset(i, null);
            candlestickPlot.setRenderer(i, null);
        }
         // Clear subplots (if any were created for indicators)
        List<?> subplots = new ArrayList<>(combinedPlot.getSubplots()); // Copy to avoid ConcurrentModificationException
        for (int i = subplots.size() - 1; i > 0; i--) { // Start from last, don't remove the main candlestick plot (index 0)
            combinedPlot.remove((XYPlot) subplots.get(i));
        }


        // 4. Re-add all indicators using the new barSeries
        for (IndicatorConfiguration.Builder<?> icBuilder : this.indicatorConfigBuilders) {
            // The builder should have been stored. Now build and add.
            addIndicatorToPlot(icBuilder.build(), this.barSeries);
        }
        
        // 5. Attempt to restore viewport (approximate)
        if (domainAxis instanceof DateAxis) {
            DateAxis dateAxis = (DateAxis) domainAxis;
            boolean restored = false;
            if (oldLowerBound != null && oldUpperBound != null && !this.barSeries.isEmpty()) {
                long firstBarTime = this.barSeries.getBar(this.barSeries.getBeginIndex()).getEndTime().toEpochSecond() * 1000;
                long lastBarTime = this.barSeries.getBar(this.barSeries.getEndIndex()).getEndTime().toEpochSecond() * 1000;
                long newLower = Math.max(oldLowerBound.getTime(), firstBarTime);
                long newUpper = Math.min(oldUpperBound.getTime(), lastBarTime);
                
                if (newLower < newUpper && (newUpper - newLower > 1000 * 60) ) { // Check for valid and somewhat reasonable range
                     dateAxis.setRange(new Date(newLower), new Date(newUpper));
                     dateAxis.setAutoRange(false);
                     restored = true;
                }
            }
            if (!restored) {
                // Fallback: Apply a default view for the new timeframe (e.g., last N bars)
                // Use a default panel width or a fixed number of bars for this fallback
                setInitialChartViewport(this.chart, -1); // -1 indicates to use default bars
            }
        }


        // Chart title might need update
        this.chart.setTitle(this.barSeries.getName()); // Update chart title with new series name (often includes timeframe)

        return this.chart;
    }


	private JFreeChart createCandlestickChart(final BarSeries series) {
		final String seriesName = series.getName();
		final ValueAxis timeAxis = new DateAxis("Time");
		final NumberAxis valueAxis = new NumberAxis("Price/Value");
		final TacCandlestickRenderer candlestickRenderer = new TacCandlestickRenderer();
		final DefaultHighLowDataset barSeriesData = this.barSeriesConverter.convert(series);
		final XYPlot plot = new XYPlot(barSeriesData, null, valueAxis, candlestickRenderer);
		setPlotTheme(plot);
		final CombinedDomainXYPlot combinedDomainPlot = new CombinedDomainXYPlot(timeAxis);

		combinedDomainPlot.add(plot,10);
		valueAxis.setAutoRangeIncludesZero(false);
		candlestickRenderer.setAutoWidthMethod(TacCandlestickRenderer.WIDTHMETHOD_SMALLEST);
		candlestickRenderer.setDrawVolume(false);
		candlestickRenderer.setDefaultItemLabelsVisible(false);

		final JFreeChart chart = new JFreeChart(seriesName, JFreeChart.DEFAULT_TITLE_FONT,
				combinedDomainPlot, true);
		theme.apply(chart);
		dataTableModel.addEntries(barSeriesData);
		return chart;
	}

	/**
	 * Adds an indicator to the chart. The indicator can be configured with help of the {@link IndicatorConfiguration}
	 * @param indicatorConfigurationBuilder the indicatorConfiguration with the {@link Indicator}ndicator
	 * @return the {@link TacChartBuilder builder}
	 */
    public TacChartBuilder withIndicator(IndicatorConfiguration.Builder<?> indicatorConfigurationBuilder) {
        // Store the builder to be able to rebuild indicators on timeframe change
        this.indicatorConfigBuilders.add(indicatorConfigurationBuilder);
        // Don't add to plot immediately here if we are building; do it in build() or switchTimeframe()
        return this;
    }

    // Renamed and modified: This method does the actual adding to plot
    private void addIndicatorToPlot(IndicatorConfiguration<?> indicatorConfiguration, BarSeries currentSeries) {
		final CombinedDomainXYPlot combinedDomainPlot = (CombinedDomainXYPlot) this.chart.getPlot();
		final Indicator<?> indicator = indicatorConfiguration.getIndicator();
		final PlotType plotType = indicatorConfiguration.getPlotType();
		final ChartType chartType = indicatorConfiguration.getChartType();
		final String name = indicatorConfiguration.getName();
		final boolean inDataTable = indicatorConfiguration.isAddToDataTable();

		if(plotType == PlotType.OVERLAY) {
			if(chartType == ChartType.LINE) {
				final int counter = overlayIds++;
				final TimeSeriesCollection timeSeriesCollection = this.indicatorToTimeSeriesConverter.convert(indicator, name);
				final XYLineAndShapeRenderer renderer = createLineRenderer(indicatorConfiguration);
				final XYPlot candlestickPlot = (XYPlot) combinedDomainPlot.getSubplots().get(0);
				setPlotTheme(candlestickPlot);
				candlestickPlot.setRenderer(counter, renderer);
				candlestickPlot.setDataset(counter, timeSeriesCollection);
				if (inDataTable) {
					this.dataTableModel.addEntries(timeSeriesCollection);
				}
			} else if(chartType == ChartType.BAR) {
				final int counter = overlayIds++;
				final TacBarDataset barDataset = indicatorToBarDataConverter.convert(indicator, name);
				final TacBarRenderer barRenderer = createBarRenderer(indicatorConfiguration);
				final XYPlot candlestickPlot = (XYPlot) combinedDomainPlot.getSubplots().get(0);
				setPlotTheme(candlestickPlot);
				candlestickPlot.setRenderer(counter, barRenderer);
				candlestickPlot.setDataset(counter, barDataset);
				if(inDataTable) {
					this.dataTableModel.addEntries(barDataset);
				}
			}
		} else if (plotType == PlotType.SUBPLOT) {
			if(chartType == ChartType.BAR) {
				final TacBarDataset barDataset = indicatorToBarDataConverter.convert(indicator, name);
				final NumberAxis valueAxis = new NumberAxis(name);
				final TacBarRenderer barRenderer = createBarRenderer(indicatorConfiguration);
				final XYPlot barPlot = new XYPlot(barDataset, null, valueAxis, barRenderer);
				setPlotTheme(barPlot);
				valueAxis.setLabel("");
				combinedDomainPlot.add(barPlot, 1);
				if (inDataTable) {
					this.dataTableModel.addEntries(barDataset);
				}
			} else if (chartType == ChartType.LINE) {
				final TimeSeriesCollection timeSeriesCollection = this.indicatorToTimeSeriesConverter.convert(indicator, name);
				final XYLineAndShapeRenderer renderer = createLineRenderer(indicatorConfiguration);
				final NumberAxis valueAxis = new NumberAxis(name);
				final XYPlot linePlot = new XYPlot(timeSeriesCollection, null, valueAxis, renderer);
				setPlotTheme(linePlot);
				valueAxis.setLabel("");
				valueAxis.setAutoRangeIncludesZero(false);
				if (inDataTable) {
					this.dataTableModel.addEntries(timeSeriesCollection);
				}
				combinedDomainPlot.add(linePlot, 1);
			}
		}
	}

	private void setPlotTheme(XYPlot plot) {
		final Color labelColor = UIManager.getColor("Label.foreground");
		plot.setBackgroundPaint(UIManager.getColor("Panel.background"));
		if(plot.getRangeAxis() != null) {
			plot.getRangeAxis().setTickLabelPaint(labelColor);
			plot.getRangeAxis().setLabelPaint(labelColor);
		}
		if(plot.getDomainAxis() != null) {
			plot.getDomainAxis().setTickLabelPaint(labelColor);
			plot.getDomainAxis().setLabelPaint(labelColor);
		}
	}

	private TacBarRenderer createBarRenderer(IndicatorConfiguration<?> indicatorConfiguration) {
		return new TacBarRenderer(indicatorConfiguration.getColor());
	}

	private XYLineAndShapeRenderer createLineRenderer(IndicatorConfiguration<?> indicatorConfiguration) {
		XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer();
		lineRenderer.setSeriesShape(0, indicatorConfiguration.getShape());
		lineRenderer.setSeriesPaint(0, indicatorConfiguration.getColor());
		return lineRenderer;
	}


	public TacChartBuilder withTradingRecord(TradingRecord tradingRecord) {
		this.tradingRecord = tradingRecord;
		return this;
	}

	public void buildAndShow(String title) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			final JFrame frame = new JFrame(title);
			frame.setLayout(new BorderLayout());
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.add(this.build());
			frame.pack();
			frame.setVisible(true);
		});
	}

	/**
	 * Builds and shows the chart panel in a JFrame with BorderLayout
	 */
	public void buildAndShow(){
		buildAndShow("Ta4j charting");
	}

	/**
	 * Returns the multi-timeframe bar series.
	 *
	 * @return the multi-timeframe bar series
	 */
	public IBarSeriesMultiTf getMultiTfBarSeries() {
		return multiTfBarSeries;
	}

	/**
	 * Returns the BarSeries for the current timeframe.
	 * @return The current BarSeries.
	 */
	public BarSeries getCurrentBarSeries() {
		return this.barSeries;
	}
}
