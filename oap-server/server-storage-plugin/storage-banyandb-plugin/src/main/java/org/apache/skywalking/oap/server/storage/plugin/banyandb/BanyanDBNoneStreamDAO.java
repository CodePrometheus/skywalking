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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.io.IOException;

@Slf4j
public class BanyanDBNoneStreamDAO extends AbstractDAO<BanyanDBStorageClient> implements INoneStreamDAO {
    private final StorageBuilder<NoneStream> storageBuilder;

    public BanyanDBNoneStreamDAO(BanyanDBStorageClient client, StorageBuilder<NoneStream> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(Model model, NoneStream noneStream) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(model.getName());
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        StreamWrite streamWrite = getClient().createStreamWrite(
            schema.getMetadata().getGroup(), // group name
            schema.getMetadata().name(), // stream-name
            noneStream.id().build() // identity
        ); // set timestamp inside `BanyanDBConverter.StreamToStorage`
        Convert2Storage<StreamWrite> convert2Storage = new BanyanDBConverter.StreamToStorage(schema, streamWrite);
        storageBuilder.entity2Storage(noneStream, convert2Storage);
        getClient().write(streamWrite);
    }
}
