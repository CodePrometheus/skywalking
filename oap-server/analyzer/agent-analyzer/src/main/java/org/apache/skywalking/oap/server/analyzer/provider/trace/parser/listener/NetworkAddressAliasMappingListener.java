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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;

/**
 * NetworkAddressAliasMappingListener use the propagated data in the segment reference, set up the alias relationship
 * between network address and current service and instance. The alias relationship will be used in the {@link
 * RPCAnalysisListener#parseExit(SpanObject, SegmentObject)} to setup the accurate target destination service
 * and instance.
 * NetworkAddressAliasMappingListener 使用segment引用中传播的数据，在网络地址和当前服务和实例之间建立别名关系。别名关系将在
 * RPCAnalysisListener#parseExit(SpanObject, SegmentObject) 中使用，以设置准确的目标目标服务和实例。
 *
 * This is a key point of SkyWalking header propagation protocol.
 * 这是SkyWalking头部传播协议的关键点。
 * <p/>
 * 在一个分布式相同中，A 通过某 IP 调用 B，然后这个 IP 并不直接对应 B 的逻辑名称
 * NetworkAddressAliasMappingListener 的作用就是识别并记录这种地址与服务之间的关系
 * eg. A 调用 1.1.1.1:8080 这个地址，sw 会通过 NetworkAddressAliasMappingListener 知道这个地址对应 B
 * 从而将链路展示 A 调用 B，而不是 A 调用 1.1.1.1:8080
 */
@Slf4j
@ToString
@RequiredArgsConstructor
public class NetworkAddressAliasMappingListener implements EntryAnalysisListener {
    private final SourceReceiver sourceReceiver;
    private final AnalyzerModuleConfig config;
    private final NamingControl namingControl;

    @Override
    public void parseEntry(SpanObject span, SegmentObject segmentObject) {
        if (span.getSkipAnalysis()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("service instance mapping listener parse reference");
        }
        if (!span.getSpanLayer().equals(SpanLayer.MQ)) {
            span.getRefsList().forEach(segmentReference -> {
                if (RefType.CrossProcess.equals(segmentReference.getRefType())) { // 跨进程
                    final String networkAddressUsedAtPeer = namingControl.formatServiceName(
                        segmentReference.getNetworkAddressUsedAtPeer());
                    if (config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(
                        networkAddressUsedAtPeer)) {
                        /*
                         * If this network address has been set as an uninstrumented gateway, no alias should be set.
                         */
                        return;
                    }
                    final String serviceName = namingControl.formatServiceName(segmentObject.getService());
                    final String instanceName = namingControl.formatInstanceName(
                        segmentObject.getServiceInstance());

                    final NetworkAddressAliasSetup networkAddressAliasSetup = new NetworkAddressAliasSetup();
                    networkAddressAliasSetup.setAddress(networkAddressUsedAtPeer);
                    networkAddressAliasSetup.setRepresentService(serviceName);
                    networkAddressAliasSetup.setRepresentServiceNormal(true);
                    networkAddressAliasSetup.setRepresentServiceInstance(instanceName);
                    networkAddressAliasSetup.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));

                    sourceReceiver.receive(networkAddressAliasSetup);
                }

            });
        }
    }

    @Override
    public void build() {
    }

    @Override
    public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    public static class Factory implements AnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }

        @Override
        public AnalysisListener create(ModuleManager moduleManager, AnalyzerModuleConfig config) {
            return new NetworkAddressAliasMappingListener(sourceReceiver, config, namingControl);
        }
    }
}
