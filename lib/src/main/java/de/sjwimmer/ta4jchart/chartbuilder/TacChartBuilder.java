package de.sjwimmer.ta4jchart.chartbuilder;

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

import com.limemojito.trading.model.bar.Bar.Period;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

public class TacChartBuilder {

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
		return new TacChart(chart, barSeries, dataTableModel, tradingRecord, this);
	}

    // Method to be called by TacChart for timeframe switching
    public JFreeChart switchTimeframe(Period newTimeframe) {
        if (this.multiTfBarSeries == null) {
            System.err.println("Cannot switch timeframe: IBarSeriesMultiTf not provided.");
            return this.chart;
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
            System.err.println("Failed to get BarSeries for timeframe: " + newTimeframe.name() + " or series is empty.");
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
        if (domainAxis instanceof DateAxis && oldLowerBound != null && oldUpperBound != null) {
            DateAxis dateAxis = (DateAxis) domainAxis;
            // This is a simple restoration. More sophisticated logic might find the
            // closest bar in the new series to the old center.
            // For now, just set range. It might auto-adjust if data doesn't fit.
            if (!this.barSeries.isEmpty()) {
                long firstBarTime = this.barSeries.getBar(this.barSeries.getBeginIndex()).getEndTime().toEpochSecond() * 1000;
                long lastBarTime = this.barSeries.getBar(this.barSeries.getEndIndex()).getEndTime().toEpochSecond() * 1000;

                long newLower = Math.max(oldLowerBound.getTime(), firstBarTime);
                long newUpper = Math.min(oldUpperBound.getTime(), lastBarTime);

                if (newLower < newUpper) {
                     dateAxis.setRange(new Date(newLower), new Date(newUpper));
                } else {
                    // Fallback if old range is outside new series range
                    dateAxis.setAutoRange(true); // Let it decide
                }
            } else {
                 dateAxis.setAutoRange(true);
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
		candlestickRenderer.setAutoWidthMethod(1);
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
}
