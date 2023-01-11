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

package com.hazelcast.internal.usercodedeployment.impl.operation;

import com.hazelcast.internal.usercodedeployment.UserCodeDeploymentService;
import com.hazelcast.internal.usercodedeployment.impl.UserCodeDeploymentSerializerHook;
import com.hazelcast.internal.util.UUIDSerializationUtil;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.impl.operationservice.Operation;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operation to distribute class definitions uploaded from client to cluster
 */
public class DeployClassesOperation extends Operation implements IdentifiedDataSerializable {

    private List<Map.Entry<String, byte[]>> classDefinitions;
    // new in 5.3
    private UUID scopeUuid;

    public DeployClassesOperation(List<Map.Entry<String, byte[]>> classDefinitions, UUID scopeUuid) {
        this.classDefinitions = classDefinitions;
        this.scopeUuid = scopeUuid;
    }

    public DeployClassesOperation() {
    }

    @Override
    public void run() throws Exception {
        UserCodeDeploymentService service = getService();
        service.defineClasses(classDefinitions, scopeUuid);
    }

    @Override
    public String getServiceName() {
        return UserCodeDeploymentService.SERVICE_NAME;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeInt(classDefinitions.size());
        for (Map.Entry<String, byte[]> classDefinition : classDefinitions) {
            out.writeString(classDefinition.getKey());
            out.writeByteArray(classDefinition.getValue());
        }
        // TODO 5.3
        UUIDSerializationUtil.writeUUID(out, scopeUuid);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        int length = in.readInt();
        classDefinitions = new ArrayList<Map.Entry<String, byte[]>>(length);
        for (int i = 0; i < length; i++) {
            String className = in.readString();
            byte[] classDefinition = in.readByteArray();
            classDefinitions.add(new AbstractMap.SimpleEntry<String, byte[]>(className, classDefinition));
        }
        // TODO 5.3
        scopeUuid = UUIDSerializationUtil.readUUID(in);
    }

    @Override
    public int getFactoryId() {
        return UserCodeDeploymentSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return UserCodeDeploymentSerializerHook.DEPLOY_CLASSES_OP;
    }
}
