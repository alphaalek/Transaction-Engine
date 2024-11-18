package dk.superawesome.core;

import dk.superawesome.core.db.DatabaseExecutor;
import dk.superawesome.core.db.DatabaseSettings;
import dk.superawesome.core.db.Requester;
import dk.superawesome.core.exceptions.RequestSetupException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EngineRequest<N extends Node> {

    public static class Builder<N extends Node, B extends Builder<N, B>> {

        @SuppressWarnings("unchecked")
        public static <N extends Node, B extends Builder<N, B>> B makeRequest(Class<? extends Builder<N, B>> clazz, EngineCache<N> cache, DatabaseSettings settings, DatabaseExecutor executor, Requester requester) throws RequestSetupException {
            try {
                return (B) clazz.getDeclaredConstructor(EngineCache.class, DatabaseSettings.class, DatabaseExecutor.class, Requester.class)
                        .newInstance(cache, settings, executor, requester);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                throw new RequestSetupException();
            }
        }

        protected final EngineRequest<N> request;

        public Builder(EngineCache<N> cache, DatabaseSettings settings, DatabaseExecutor<N> executor, Requester requester) {
            this.request = new EngineRequest<>(cache, settings, executor, requester);
        }

        public B addFilter(QueryFilter.FilterType<?, ? super N> type, QueryFilter<? super N> filter) {
            this.request.addFilter(type, filter);
            return (B) this;
        }

        public EngineRequest<N> build() {
            return this.request;
        }
    }

    private final List<QueryFilter.FilterData<N>> filters = new ArrayList<>();
    private final EngineCache<N> cache;
    private final DatabaseSettings settings;
    private final DatabaseExecutor<N> executor;
    private final Requester requester;

    public EngineRequest(EngineCache<N> cache, DatabaseSettings settings, DatabaseExecutor<N> executor, Requester requester) {
        this.cache = cache;
        this.settings = settings;
        this.executor = executor;
        this.requester = requester;
    }

    public void removeAllFiltersOf(QueryFilter.FilterType<?, N> type) {
        this.filters.removeIf(f -> f.type().equals(type));
    }

    public List<QueryFilter.FilterData<N>> allFiltersOf(QueryFilter.FilterType<?, N> type) {
        return this.filters.stream().filter(f -> f.type().equals(type)).collect(Collectors.toList());
    }

    public void addFilter(QueryFilter.FilterType<?, ? super N> type, QueryFilter<? super N> filter) {
        this.filters.add(new QueryFilter.FilterData<>(type, filter));
    }

    public boolean filter(N node) {
        return this.filters.stream().allMatch(f -> f.filter().test(node));
    }

    public List<QueryFilter.FilterData<N>> getFilters() {
        return this.filters;
    }

    public EngineCache<N> getCache() {
        return this.cache;
    }

    public DatabaseSettings getSettings() {
        return this.settings;
    }

    public DatabaseExecutor<N> getExecutor() {
        return this.executor;
    }

    public Requester getRequester() {
        return this.requester;
    }
}
