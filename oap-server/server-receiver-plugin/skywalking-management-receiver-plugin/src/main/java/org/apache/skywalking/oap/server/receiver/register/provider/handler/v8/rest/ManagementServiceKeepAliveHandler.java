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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.rest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

public class ManagementServiceKeepAliveHandler extends JettyJsonHandler {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final Gson gson = new Gson();

    public ManagementServiceKeepAliveHandler(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
    }

    @Override
    protected JsonElement doPost(final HttpServletRequest req) throws ArgumentsParseException, IOException {
        final InstanceProperties.Builder request = InstanceProperties.newBuilder();
        ProtoBufJsonUtils.fromJSON(getJsonBody(req), request);

        final String serviceName = namingControl.formatServiceName(request.getService());
        final String instanceName = namingControl.formatInstanceName(request.getServiceInstance());

        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
        ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
        serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, true));
        serviceInstanceUpdate.setName(instanceName);
        serviceInstanceUpdate.setTimeBucket(timeBucket);
        serviceInstanceUpdate.setLayer(Layer.GENERAL);
        sourceReceiver.receive(serviceInstanceUpdate);

        ServiceMeta serviceMeta = new ServiceMeta();
        serviceMeta.setName(serviceName);
        serviceMeta.setNormal(true);
        serviceMeta.setTimeBucket(timeBucket);
        serviceMeta.setLayer(Layer.GENERAL);
        sourceReceiver.receive(serviceMeta);

        return gson.fromJson(ProtoBufJsonUtils.toJSON(Commands.newBuilder().build()), JsonElement.class);
    }

    @Override
    public String pathSpec() {
        return "/v3/management/keepAlive";
    }
}
