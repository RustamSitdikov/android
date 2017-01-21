/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.cpu;


import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CpuProfilerStage extends Stage {

  private static final SingleUnitAxisFormatter CPU_USAGE_FORMATTER = new SingleUnitAxisFormatter(1, 5, 10, "%");
  private static final SingleUnitAxisFormatter NUM_THREADS_AXIS = new SingleUnitAxisFormatter(1, 5, 1, "");
  private final CpuThreadsModel myThreadsStates;
  private final AxisComponentModel myCpuUsageAxis;
  private final AxisComponentModel myThreadCountAxis;
  private final DetailedCpuUsage myCpuUsage;
  private final CpuStageLegends myLegends;
  private final DurationDataModel<CpuCapture> myTraceDurations;
  private final EventMonitor myEventMonitor;

  /**
   * The thread states combined with the capture states.
   */
  public enum ThreadState {
    RUNNING,
    RUNNING_CAPTURED,
    SLEEPING,
    SLEEPING_CAPTURED,
    DEAD,
    DEAD_CAPTURED,
    WAITING,
    WAITING_CAPTURED,
    UNKNOWN
  }

  private static final Logger LOG = Logger.getInstance(CpuProfilerStage.class);
  @NotNull
  private final CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull
  private final CpuTraceDataSeries myCpuTraceDataSeries;
  private AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  /**
   * The current capture.
   */
  @Nullable
  private CpuCapture myCapture;
  /**
   * Whether there is a capture in progress.
   * TODO: Timeouts. Also, capturing state should come from the device instead of being kept here.
   */
  private boolean myCapturing;

  /**
   * Whether the capture is being parsed.
   */
  private boolean myParsingCapture;

  /**
   * Id of the current selected thread.
   */
  private int mySelectedThread;

  /**
   * A cache of already parsed captures, indexed by trace_id.
   */
  private Map<Integer, CpuCapture> myTraceCaptures = new HashMap<>();

  public CpuProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myCpuService = getStudioProfilers().getClient().getCpuClient();
    myCpuTraceDataSeries = new CpuTraceDataSeries();

    Range viewRange = getStudioProfilers().getTimeline().getViewRange();
    Range dataRange = getStudioProfilers().getTimeline().getDataRange();

    myCpuUsage = new DetailedCpuUsage(profilers);

    myCpuUsageAxis = new AxisComponentModel(myCpuUsage.getCpuRange(), CPU_USAGE_FORMATTER);
    myCpuUsageAxis.setClampToMajorTicks(true);

    myThreadCountAxis = new AxisComponentModel(myCpuUsage.getThreadRange(), NUM_THREADS_AXIS);
    myThreadCountAxis.setClampToMajorTicks(true);

    myLegends = new CpuStageLegends(myCpuUsage, dataRange);

    // Create an event representing the traces within the range.
    myTraceDurations = new DurationDataModel<>(new RangedSeries<>(viewRange, getCpuTraceDataSeries()));
    myThreadsStates = new CpuThreadsModel(viewRange, this, getStudioProfilers().getProcessId(), getStudioProfilers().getDeviceSerial());

    myEventMonitor = new EventMonitor(profilers);
  }

  public AxisComponentModel getCpuUsageAxis() {
    return myCpuUsageAxis;
  }

  public AxisComponentModel getThreadCountAxis() {
    return myThreadCountAxis;
  }

  public DetailedCpuUsage getCpuUsage() {
    return myCpuUsage;
  }

  public CpuStageLegends getLegends() {
    return myLegends;
  }

  public DurationDataModel<CpuCapture> getTraceDurations() {
    return myTraceDurations;
  }

  public String getName() {
    return "CPU";
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @Override
  public void enter() {
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myCpuUsage);
    getStudioProfilers().getUpdater().register(myTraceDurations);
    getStudioProfilers().getUpdater().register(myCpuUsageAxis);
    getStudioProfilers().getUpdater().register(myThreadCountAxis);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myThreadsStates);
  }

  @Override
  public void exit() {
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myCpuUsage);
    getStudioProfilers().getUpdater().unregister(myTraceDurations);
    getStudioProfilers().getUpdater().unregister(myCpuUsageAxis);
    getStudioProfilers().getUpdater().unregister(myThreadCountAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myThreadsStates);
  }

  @Override
  public ProfilerMode getProfilerMode() {
    return myCapture == null ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  public void startCapturing() {
    CpuProfiler.CpuProfilingAppStartRequest request = CpuProfiler.CpuProfilingAppStartRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStartRequest.Profiler.ART) // TODO: support simpleperf
      .setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED) // TODO: support instrumented mode
      .build();

    // TODO: calls to start/stop freeze the UI for a noticeable amount of time. We need to fix it.
    CpuProfiler.CpuProfilingAppStartResponse response = myCpuService.startProfilingApp(request);

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS)) {
      LOG.warn("Unable to start tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
      myCapturing = false;
      myParsingCapture = false;
    }
    else {
      myCapturing = true;
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public void stopCapturing() {
    CpuProfiler.CpuProfilingAppStopRequest request = CpuProfiler.CpuProfilingAppStopRequest.newBuilder()
      .setAppPkgName(getStudioProfilers().getProcess().getName()) // TODO: Investigate if this is the right way of choosing the app
      .setProfiler(CpuProfiler.CpuProfilingAppStopRequest.Profiler.ART) // TODO: support simpleperf
      .build();

    CpuProfiler.CpuProfilingAppStopResponse response = myCpuService.stopProfilingApp(request);

    if (!response.getStatus().equals(CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS)) {
      LOG.warn("Unable to stop tracing: " + response.getStatus());
      LOG.warn(response.getErrorMessage());
    }
    else {
      ListenableFutureTask<CpuCapture> task = ListenableFutureTask.create(() -> new CpuCapture(response.getTrace()));
      Futures.addCallback(task, new CpuCaptureCallback(response.getTraceId()), getStudioProfilers().getIdeServices().getProfilerExecutor());
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(task);
      executor.shutdown();
      myParsingCapture = true;
    }
    myCapturing = false;
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public void setCapture(@Nullable CpuCapture capture) {
    myCapture = capture;
    if (capture != null) {
      ProfilerTimeline timeline = getStudioProfilers().getTimeline();
      timeline.setStreaming(false);
      timeline.getSelectionRange().set(myCapture.getRange());
      getStudioProfilers().modeChanged();
    }
    myAspect.changed(CpuProfilerAspect.CAPTURE);
  }

  public int getSelectedThread() {
    return mySelectedThread;
  }

  public void setSelectedThread(int id) {
    mySelectedThread = id;
    myAspect.changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  /**
   * The current capture of the cpu profiler, if null there is no capture to display otherwise we need to be in
   * a capture viewing mode.
   */
  @Nullable
  public CpuCapture getCapture() {
    return myCapture;
  }

  public boolean isCapturing() {
    // TODO: get this information from perfd rather than keeping it in a flag
    return myCapturing;
  }

  public boolean isParsingCapture() {
    return myParsingCapture;
  }

  @NotNull
  public CpuTraceDataSeries getCpuTraceDataSeries() {
    return myCpuTraceDataSeries;
  }

  @NotNull
  public CpuThreadsModel getThreadStates() {
    return myThreadsStates;
  }

  public CpuCapture getCapture(int traceId) {
    CpuCapture capture = myTraceCaptures.get(traceId);
    if (capture == null) {
      CpuProfiler.GetTraceRequest request = CpuProfiler.GetTraceRequest.newBuilder()
        .setProcessId(getStudioProfilers().getProcessId())
        .setDeviceSerial(getStudioProfilers().getDeviceSerial())
        .setTraceId(traceId)
        .build();
      CpuProfiler.GetTraceResponse trace = myCpuService.getTrace(request);
      if (trace.getStatus() == CpuProfiler.GetTraceResponse.Status.SUCCESS) {
        // TODO: move this parsing to a separate thread
        try {
          capture = new CpuCapture(trace.getData());
        } catch (IllegalStateException e) {
          // Don't crash studio if parsing fails.
        }
      }
      // TODO: Limit how many captures we keep parsed in memory
      myTraceCaptures.put(traceId, capture);
    }
    return capture;
  }

  @VisibleForTesting
  class CpuTraceDataSeries implements DataSeries<CpuCapture> {
    @Override
    public ImmutableList<SeriesData<CpuCapture>> getDataForXRange(Range xRange) {
      long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMin());
      long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)xRange.getMax());

      CpuProfiler.GetTraceInfoResponse response = myCpuService.getTraceInfo(
        CpuProfiler.GetTraceInfoRequest.newBuilder().
        setProcessId(getStudioProfilers().getProcessId()).
        setDeviceSerial(getStudioProfilers().getDeviceSerial()).
        setFromTimestamp(rangeMin).setToTimestamp(rangeMax).build());

      List<SeriesData<CpuCapture>> seriesData = new ArrayList<>();
      for (CpuProfiler.TraceInfo traceInfo : response.getTraceInfoList()) {
        CpuCapture capture = getCapture(traceInfo.getTraceId());
        if (capture != null) {
          Range range = capture.getRange();
          seriesData.add(new SeriesData<>((long)range.getMin(), capture));
        }
      }
      return ContainerUtil.immutableList(seriesData);
    }
  }

  public static class CpuStageLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myOthersLegend;
    @NotNull private final SeriesLegend myThreadsLegend;

    public CpuStageLegends(@NotNull DetailedCpuUsage cpuUsage, @NotNull Range dataRange) {
      myCpuLegend = new SeriesLegend(cpuUsage.getCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myOthersLegend = new SeriesLegend(cpuUsage.getOtherCpuSeries(), CPU_USAGE_FORMATTER, dataRange);
      myThreadsLegend = new SeriesLegend(cpuUsage.getThreadsCountSeries(), NUM_THREADS_AXIS, dataRange);
      add(myCpuLegend);
      add(myOthersLegend);
      add(myThreadsLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public SeriesLegend getOthersLegend() {
      return myOthersLegend;
    }

    @NotNull
    public SeriesLegend getThreadsLegend() {
      return myThreadsLegend;
    }
  }

  private class CpuCaptureCallback implements FutureCallback<CpuCapture> {

    private int myTraceId;

    private CpuCaptureCallback(int traceId) {
      myTraceId = traceId;
    }

    @Override
    public void onSuccess(@Nullable CpuCapture capture) {
      // If onSuccess is called, it means we successfully created a new CpuCapture.
      // Therefore, the value shouldn't be null;
      assert capture != null;

      myTraceCaptures.put(myTraceId, capture);
      myParsingCapture = false;
      setCapture(capture);
      setSelectedThread(capture.getMainThreadId());
    }

    @Override
    public void onFailure(@NotNull Throwable e) {
      LOG.warn("Unable to parse capture: " + e.getMessage());
      myParsingCapture = false;
      setCapture(null);
      // Set an invalid thread id to clear the thread selection
      setSelectedThread(-1);
    }
  }
}
