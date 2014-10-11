package com.spotify.heroic.metric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.spotify.heroic.aggregation.AggregationGroup;
import com.spotify.heroic.aggregationcache.AggregationCache;
import com.spotify.heroic.async.Future;
import com.spotify.heroic.async.Futures;
import com.spotify.heroic.filter.Filter;
import com.spotify.heroic.metric.async.TimeSeriesTransformer;
import com.spotify.heroic.metric.error.BackendOperationException;
import com.spotify.heroic.metric.model.FetchData;
import com.spotify.heroic.metric.model.GroupedSeries;
import com.spotify.heroic.metric.model.MetricGroups;
import com.spotify.heroic.metric.model.WriteBatchResult;
import com.spotify.heroic.metric.model.WriteMetric;
import com.spotify.heroic.model.DateRange;
import com.spotify.heroic.model.Series;
import com.spotify.heroic.statistics.MetricManagerReporter;

@Slf4j
@RequiredArgsConstructor
public class MetricBackends {
    private final AggregationCache cache;
    private final MetricManagerReporter reporter;
    private final int disabled;
    private final List<MetricBackend> backends;

    public static interface BackendOp {
        void run(int disabled, MetricBackend backend) throws Exception;
    }

    private void execute(BackendOp op) {
        for (final MetricBackend b : backends) {
            try {
                op.run(disabled, b);
            } catch (final Exception e) {
                log.error("Backend operation failed", e);
            }
        }
    }

    /**
     * Perform a direct query on the configured backends.
     *
     * @param key
     *            Key of series to query.
     * @param series
     *            Set of series to query.
     * @param range
     *            Range of series to query.
     * @param aggregation
     *            Aggregation method to use.
     * @return The result in the form of MetricGroups.
     * @throws BackendOperationException
     */
    public Future<MetricGroups> groupedQuery(final Map<String, String> group, final Filter filter,
            final Set<Series> series, final DateRange range, final AggregationGroup aggregation) {
        final TimeSeriesTransformer transformer = new TimeSeriesTransformer(cache, filter, aggregation, range);

        return groupTimeseries(group, series).transform(transformer).register(reporter.reportRpcQueryMetrics());
    }

    public List<Future<FetchData>> query(final Series series, final DateRange range) {
        final List<Future<FetchData>> callbacks = new ArrayList<>();

        execute(new BackendOp() {
            @Override
            public void run(int disabled, MetricBackend backend) throws Exception {
                callbacks.addAll(backend.fetch(series, range));
            }
        });

        return callbacks;
    }

    private Future<List<GroupedSeries>> groupTimeseries(final Map<String, String> group, final Set<Series> series) {
        final List<GroupedSeries> grouped = new ArrayList<>();

        execute(new BackendOp() {
            @Override
            public void run(final int disabled, final MetricBackend backend) throws Exception {
                // do not cache results if any backends are disabled or
                // unavailable,
                // because that would contribute to messed up results.
                final boolean noCache = disabled > 0;

                grouped.add(new GroupedSeries(group, backend, series, noCache));
            }
        });

        return Futures.resolved(grouped);
    }

    /**
     * Perform a direct write on available configured backends.
     *
     * @param writes
     *            Batch of writes to perform.
     * @return A callback indicating how the writes went.
     * @throws BackendOperationException
     */
    public Future<WriteBatchResult> write(final Collection<WriteMetric> writes) {
        final List<Future<WriteBatchResult>> callbacks = new ArrayList<>();

        execute(new BackendOp() {
            @Override
            public void run(int disabled, MetricBackend backend) throws Exception {
                callbacks.add(backend.write(writes));
            }
        });

        return Futures.reduce(callbacks, WriteBatchResult.merger());
    }
}