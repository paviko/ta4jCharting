package de.sjwimmer.ta4jchart.chartbuilder.listener;

import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.IDynamicDataset;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.data.Range;

import javax.swing.Timer; // Swing Timer for EDT safety
import java.util.ArrayList;
import java.util.List;

public class DomainAxisRangeChangeHandler implements AxisChangeListener {
    private final List<IDynamicDataset> dynamicDatasets = new ArrayList<>();
    private final ValueAxis domainAxis;
    private final Timer debounceTimer;
    private static final int DEBOUNCE_DELAY_MS = 10;

    public DomainAxisRangeChangeHandler(ValueAxis domainAxis, IDynamicDataset initialDataset) {
        this.domainAxis = domainAxis;
        if (initialDataset != null) {
            this.dynamicDatasets.add(initialDataset);
        }
        this.debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> performUpdate());
        this.debounceTimer.setRepeats(false);
    }

    public void addDataset(IDynamicDataset dataset) {
        if (dataset != null && !this.dynamicDatasets.contains(dataset)) {
            this.dynamicDatasets.add(dataset);
            // Immediately update the new dataset to the current view
            Range range = domainAxis.getRange();
            if (range != null) {
                dataset.updateWindow((long) range.getLowerBound(), (long) range.getUpperBound());
            }
        }
    }

    public void clearAllDatasets() {
        this.dynamicDatasets.clear();
    }

    private long lastUpdateTime = 0;
    private boolean updateScheduled = false;

    @Override
    public void axisChanged(AxisChangeEvent event) {
        long now = System.currentTimeMillis();
        if (!updateScheduled) {
            if (now - lastUpdateTime > DEBOUNCE_DELAY_MS) {
                performUpdate();
                lastUpdateTime = now;
            } else {
                debounceTimer.restart();
                updateScheduled = true;
            }
        } else {
            debounceTimer.restart();
        }
    }

    private void performUpdate() {
        updateScheduled = false;
        debounceTimer.stop();

        if (domainAxis == null || dynamicDatasets.isEmpty()) return;
        Range range = domainAxis.getRange();
        if (range == null) return;

        long lower = (long) range.getLowerBound();
        long upper = (long) range.getUpperBound();

        for (IDynamicDataset dataset : new ArrayList<>(dynamicDatasets)) { // Iterate over a copy
            dataset.updateWindow(lower, upper);
        }
        lastUpdateTime = System.currentTimeMillis();
    }

}