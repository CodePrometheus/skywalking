/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.type.Owner;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

public class AggregationQueryService implements Service {
    private final ModuleManager moduleManager;
    private IAggregationQueryDAO aggregationQueryDAO;

    public AggregationQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IAggregationQueryDAO getAggregationQueryDAO() {
        if (aggregationQueryDAO == null) {
            aggregationQueryDAO = moduleManager.find(StorageModule.NAME)
                                               .provider()
                                               .getService(IAggregationQueryDAO.class);
        }
        return aggregationQueryDAO;
    }

    private List<SelectedRecord> invokeSortMetrics(TopNCondition condition, Duration duration) throws IOException {
        if (!condition.senseScope()) {
            return Collections.emptyList();
        }
        final String valueCName = ValueColumnMetadata.INSTANCE.getValueCName(condition.getName());
        List<KeyValue> additionalConditions = null;
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            if (condition.getNormal() == null) {
                return Collections.emptyList();
            }
            additionalConditions = new ArrayList<>(1);
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.getNormal());
            additionalConditions.add(new KeyValue(InstanceTraffic.SERVICE_ID, serviceId));
        }
        final List<SelectedRecord> selectedRecords = getAggregationQueryDAO().sortMetricsDebuggable(
            condition, valueCName, duration, additionalConditions);
        selectedRecords.forEach(selectedRecord -> {
            Owner owner = new Owner();
            owner.setScope(condition.getScope());
            selectedRecord.setOwner(owner);
            switch (condition.getScope()) {
                case Service:
                    final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition
                        = IDManager.ServiceID.analysisId(selectedRecord.getId());
                    selectedRecord.setName(serviceIDDefinition.getName());
                    owner.setServiceID(selectedRecord.getId());
                    owner.setServiceName(serviceIDDefinition.getName());
                    owner.setNormal(serviceIDDefinition.isReal());
                    break;
                case ServiceInstance:
                    final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition
                        = IDManager.ServiceInstanceID.analysisId(selectedRecord.getId());
                    final IDManager.ServiceID.ServiceIDDefinition instanceServiceIDDefinition =
                        IDManager.ServiceID.analysisId(instanceIDDefinition.getServiceId());
                    /*
                     * Add the service name into the name if this is global top N.
                     */
                    if (StringUtil.isEmpty(condition.getParentService())) {
                        selectedRecord.setName(instanceServiceIDDefinition.getName() + " - " + instanceIDDefinition.getName());
                    } else {
                        selectedRecord.setName(instanceIDDefinition.getName());
                    }
                    owner.setServiceID(instanceIDDefinition.getServiceId());
                    owner.setServiceName(instanceServiceIDDefinition.getName());
                    owner.setNormal(instanceServiceIDDefinition.isReal());
                    owner.setServiceInstanceID(selectedRecord.getId());
                    owner.setServiceInstanceName(instanceIDDefinition.getName());
                    break;
                case Endpoint:
                    final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition
                        = IDManager.EndpointID.analysisId(selectedRecord.getId());
                    final IDManager.ServiceID.ServiceIDDefinition endpointServiceIDDefinition =
                        IDManager.ServiceID.analysisId(endpointIDDefinition.getServiceId());
                    /*
                     * Add the service name into the name if this is global top N.
                     */
                    if (StringUtil.isEmpty(condition.getParentService())) {
                        selectedRecord.setName(endpointServiceIDDefinition.getName()
                                                   + " - " + endpointIDDefinition.getEndpointName());
                    } else {
                        selectedRecord.setName(endpointIDDefinition.getEndpointName());
                    }
                    owner.setServiceID(endpointIDDefinition.getServiceId());
                    owner.setServiceName(endpointServiceIDDefinition.getName());
                    owner.setNormal(endpointServiceIDDefinition.isReal());
                    owner.setEndpointID(selectedRecord.getId());
                    owner.setEndpointName(endpointIDDefinition.getEndpointName());
                    break;
                default:
                    selectedRecord.setName(Const.UNKNOWN);
            }
        });
        return selectedRecords;
    }

    public List<SelectedRecord> sortMetrics(TopNCondition condition,
                                            Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Service: sortMetrics");
                span.setMsg("TopNCondition: " + condition + ", Duration: " + duration);
            }
            return invokeSortMetrics(condition, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }
}
