package de.sjwimmer.ta4jchart.chartbuilder.listener;

import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.data.Range;
import javax.swing.Timer; // Swing Timer for EDT safety

public class DomainAxisRangeChangeHandler implements AxisChangeListener {
    private final DynamicWindowOHLCDataset dynamicDataset;
    private final ValueAxis domainAxis;
    private final Timer debounceTimer;
    private static final int DEBOUNCE_DELAY_MS = 10; // Adjust as needed

    public DomainAxisRangeChangeHandler(DynamicWindowOHLCDataset dynamicDataset, ValueAxis domainAxis) {
        this.dynamicDataset = dynamicDataset;
        this.domainAxis = domainAxis;
        this.debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> performUpdate());
        this.debounceTimer.setRepeats(false);
    }

    // METHOD 1: waiting until scrolling stops
    // @Override
    // public void axisChanged(AxisChangeEvent event) {
    //     debounceTimer.restart();
    // }

    // private void performUpdate() {
    //     if (domainAxis == null || dynamicDataset == null) return;
    //     Range range = domainAxis.getRange();
    //     if (range == null) return;

    //     dynamicDataset.updateWindow((long) range.getLowerBound(), (long) range.getUpperBound());
    // }

    // METHOD 2: leading edge debounce
    private long lastUpdateTime = 0;
    private boolean updateScheduled = false;
    
    @Override
    public void axisChanged(AxisChangeEvent event) {
        long now = System.currentTimeMillis();
        if (!updateScheduled) { // If no update is currently pending from a timer
            if (now - lastUpdateTime > DEBOUNCE_DELAY_MS) { // If enough time passed since last immediate update
                performUpdate(); // Perform update immediately (leading edge)
                lastUpdateTime = now;
            } else {
                // Too soon since last immediate update, schedule it via timer
                debounceTimer.restart(); // Standard trailing edge for subsequent quick events
                updateScheduled = true;
            }
        } else {
            // An update is already scheduled by the timer, just restart it
            debounceTimer.restart();
        }
    }
    
    private void performUpdate() {
        updateScheduled = false; // Reset scheduling flag
        debounceTimer.stop();   // Stop timer in case it was running
    
        if (domainAxis == null || dynamicDataset == null) return;
        Range range = domainAxis.getRange();
        if (range == null) return;
    
        dynamicDataset.updateWindow((long) range.getLowerBound(), (long) range.getUpperBound());
        lastUpdateTime = System.currentTimeMillis(); // Record time of this actual update
    }
}
