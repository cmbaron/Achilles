/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package info.archinnov.achilles.internal.context;

import static com.google.common.cache.CacheBuilder.newBuilder;
import static com.google.common.collect.Maps.filterValues;
import static com.google.common.collect.Maps.transformValues;
import static info.archinnov.achilles.internal.metadata.holder.EntityMeta.CLUSTERED_COUNTER_FILTER;
import static info.archinnov.achilles.internal.metadata.holder.EntityMeta.EXCLUDE_CLUSTERED_COUNTER_FILTER;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import info.archinnov.achilles.counter.AchillesCounter.CQLQueryType;
import info.archinnov.achilles.internal.metadata.holder.EntityMeta;
import info.archinnov.achilles.internal.metadata.parsing.context.ParsingResult;
import info.archinnov.achilles.internal.statement.cache.CacheManager;
import info.archinnov.achilles.internal.statement.cache.StatementCacheKey;
import info.archinnov.achilles.internal.statement.prepared.PreparedStatementGenerator;

public class DaoContextFactory {
    private static final Logger log = LoggerFactory.getLogger(DaoContextFactory.class);

    private PreparedStatementGenerator queryGenerator = new PreparedStatementGenerator();

    public DaoContext create(Session session, ParsingResult parsingResult, ConfigurationContext configContext) {
        log.debug("Build DaoContext");

        Map<Class<?>, EntityMeta> metaMap = parsingResult.getMetaMap();

        Map<Class<?>, PreparedStatement> insertPSMap = new HashMap<>(transformValues(
                filterValues(metaMap, EXCLUDE_CLUSTERED_COUNTER_FILTER), getInsertPSTransformer(session)));

        Map<Class<?>, PreparedStatement> selectPSMap = new HashMap<>(transformValues(metaMap,
                                                                                     getSelectPSTransformer(session)));

        Map<Class<?>, Map<String, PreparedStatement>> removePSMap = new HashMap<>(transformValues(
                filterValues(metaMap, EXCLUDE_CLUSTERED_COUNTER_FILTER), getRemovePSTransformer(session)));

        Cache<StatementCacheKey, PreparedStatement> dynamicPSCache = newBuilder().maximumSize(
                configContext.getPreparedStatementLRUCacheSize()).build();

        Map<CQLQueryType, PreparedStatement> counterQueryMap;
        if (parsingResult.hasSimpleCounter()) {
            counterQueryMap = queryGenerator.prepareSimpleCounterQueryMap(session);
        } else {
            counterQueryMap = ImmutableMap.of();
        }

        Map<Class<?>, Map<CQLQueryType, Map<String, PreparedStatement>>> clusteredCounterQueriesMap = new HashMap<>(
                transformValues(filterValues(metaMap, CLUSTERED_COUNTER_FILTER),
                                getClusteredCounterTransformer(session)));

        displayPreparedStatementsStats(insertPSMap, selectPSMap, removePSMap, counterQueryMap,
                                       clusteredCounterQueriesMap);

        DaoContext daoContext = new DaoContext();
        daoContext.setInsertPSs(insertPSMap);
        daoContext.setDynamicPSCache(dynamicPSCache);
        daoContext.setSelectPSs(selectPSMap);
        daoContext.setRemovePSs(removePSMap);
        daoContext.setCounterQueryMap(counterQueryMap);
        daoContext.setClusteredCounterQueryMap(clusteredCounterQueriesMap);
        daoContext.setSession(session);
        daoContext.setCacheManager(new CacheManager(configContext.getPreparedStatementLRUCacheSize()));

        return daoContext;
    }

    Function<EntityMeta, PreparedStatement> getInsertPSTransformer(final Session session) {
        return new Function<EntityMeta, PreparedStatement>() {
            @Override
            public PreparedStatement apply(EntityMeta meta) {
                return queryGenerator.prepareInsertPS(session, meta);
            }
        };
    }

    Function<EntityMeta, PreparedStatement> getSelectPSTransformer(final Session session) {
        return new Function<EntityMeta, PreparedStatement>() {
            @Override
            public PreparedStatement apply(EntityMeta meta) {
                return queryGenerator.prepareSelectPS(session, meta);
            }
        };
    }

    Function<EntityMeta, Map<String, PreparedStatement>> getRemovePSTransformer(final Session session) {
        return new Function<EntityMeta, Map<String, PreparedStatement>>() {
            @Override
            public Map<String, PreparedStatement> apply(EntityMeta meta) {
                return queryGenerator.prepareRemovePSs(session, meta);
            }
        };
    }

    Function<EntityMeta, Map<CQLQueryType, Map<String, PreparedStatement>>> getClusteredCounterTransformer(
            final Session session) {
        return new Function<EntityMeta, Map<CQLQueryType, Map<String, PreparedStatement>>>() {
            @Override
            public Map<CQLQueryType, Map<String, PreparedStatement>> apply(EntityMeta meta) {
                return queryGenerator.prepareClusteredCounterQueryMap(session, meta);
            }
        };
    }

    private void displayPreparedStatementsStats(Map<Class<?>, PreparedStatement> insertPSMap, Map<Class<?>,
            PreparedStatement> selectPSMap, Map<Class<?>, Map<String, PreparedStatement>> removePSMap,
                                                Map<CQLQueryType, PreparedStatement> counterQueryMap, Map<Class<?>,
            Map<CQLQueryType, Map<String, PreparedStatement>>> clusteredCounterQueriesMap) {
        log.info("Prepare {} INSERT, {} SELECT, {} DELETE, {} COUNTER SELECT and {} CLUSTERED COUNTER SELECT " +
                         "statements", insertPSMap.size(), selectPSMap.size(), removePSMap.size(), counterQueryMap
                         .size(), clusteredCounterQueriesMap.size());
        int totalPreparedStatementsCount = insertPSMap.size()
                + selectPSMap.size()
                + removePSMap.size()
                + counterQueryMap.size()
                + clusteredCounterQueriesMap.size();
        log.info("Total prepared statements so far : {}", totalPreparedStatementsCount);
    }
}
