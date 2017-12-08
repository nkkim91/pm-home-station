/*
 * pm-station-usb
 * 2017 (C) Copyright - https://github.com/rjaros87/pm-station-usb
 * License: GPL 3.0
 */
package pmstation.plantower;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pmstation.core.plantower.IPlanTowerObserver;
import pmstation.core.plantower.ParticulateMatterSample;
import pmstation.core.plantower.PlanTowerDevice;
import pmstation.serial.SerialUART;

public class PlanTowerSensor {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanTowerSensor.class);
    
    private static final long DEFAULT_INTERVAL = 3000L;

    private List<IPlanTowerObserver> planTowerObserver;
    private SerialUART serialUART;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledMeasurements = null;
    private long interval = -1;

    public PlanTowerSensor() {
        serialUART = new SerialUART();
        planTowerObserver = new ArrayList<>();
        executor = Executors.newScheduledThreadPool(1);
    }

    public boolean connectDevice() {
        boolean openPort = serialUART.openPort();
        if (openPort) {
            serialUART.writeBytes(PlanTowerDevice.MODE_WAKEUP);
        }
        return openPort;
    }

    public void disconnectDevice() {
        if (scheduledMeasurements != null) {
            scheduledMeasurements.cancel(true);
        }
        if (serialUART.isConnected()) {
            serialUART.writeBytes(PlanTowerDevice.MODE_SLEEP);
            serialUART.closePort();
        }
    }

    public void startMeasurements() {
        startMeasurements(DEFAULT_INTERVAL);
    }
    
    public synchronized void startMeasurements(long interval) {
        if (scheduledMeasurements != null && !scheduledMeasurements.isDone()) {
            if (this.interval == interval) {
                logger.info("Ignoring re-scheduling since the interval is the same as previous one");
                return;
            } else {
                logger.info("Going to cancel scheduled measurement task as interval change is requested");
                scheduledMeasurements.cancel(true);
            }
        }
        logger.info("Scheduling measurements at interval: {}ms...", interval);
        scheduledMeasurements = executor.scheduleAtFixedRate(getMeasurementsRunnable(), 0, interval, TimeUnit.MILLISECONDS);
        this.interval = interval;
    }

    public void addObserver(IPlanTowerObserver observer) {
        planTowerObserver.add(observer);
    }
    
    public boolean isConnected() {
        return serialUART.isConnected();
    }
    
    private Runnable getMeasurementsRunnable() {
        return () -> {
            byte[] readBuffer;
            readBuffer = serialUART.readBytes(2 * PlanTowerDevice.DATA_LENGTH);
            notifyAllObservers(PlanTowerDevice.parse(readBuffer));
        };
    }

    private void notifyAllObservers(ParticulateMatterSample particulateMatterSample) {
        if (particulateMatterSample != null) {
            for (IPlanTowerObserver observer : planTowerObserver) {
                observer.update(particulateMatterSample);
            }
        }
    }
}
