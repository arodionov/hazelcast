/*
 * Copyright (c) 2008-2022, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.impl.spi.impl;

import com.hazelcast.client.config.ClientUserCodeDeploymentConfig;
import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.ClientDeployClassesCodec;
import com.hazelcast.deployment.impl.client.CodeDeploymentUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ClientUserCodeDeploymentService {

    private final ClientUserCodeDeploymentConfig clientUserCodeDeploymentConfig;
    private final ClassLoader configClassLoader;
    //List<Map.Entry> is used instead of Map to comply with generated code of client protocol
    private final List<Map.Entry<String, byte[]>> classDefinitionList = new ArrayList<>();

    public ClientUserCodeDeploymentService(ClientUserCodeDeploymentConfig clientUserCodeDeploymentConfig,
                                           ClassLoader configClassLoader) {
        this.clientUserCodeDeploymentConfig = clientUserCodeDeploymentConfig;
        this.configClassLoader = configClassLoader != null ? configClassLoader : Thread.currentThread().getContextClassLoader();
    }

    public void start() throws IOException, ClassNotFoundException {
        if (!clientUserCodeDeploymentConfig.isEnabled()) {
            return;
        }
        List<Map.Entry<String, byte[]>> loadClasses = CodeDeploymentUtil.loadClasses(configClassLoader,
                clientUserCodeDeploymentConfig.getClassNames(),
                clientUserCodeDeploymentConfig.getJarPaths());
        classDefinitionList.addAll(loadClasses);
    }

    public void deploy(HazelcastClientInstanceImpl client) throws ExecutionException, InterruptedException {
        if (!clientUserCodeDeploymentConfig.isEnabled() || classDefinitionList.isEmpty()) {
            return;
        }

        ClientMessage request = ClientDeployClassesCodec.encodeRequest(classDefinitionList);
        ClientInvocation invocation = new ClientInvocation(client, request, null);
        ClientInvocationFuture future = invocation.invokeUrgent();
        future.get();
    }

    //testing purposes
    public List<Map.Entry<String, byte[]>> getClassDefinitionList() {
        return classDefinitionList;
    }
}
