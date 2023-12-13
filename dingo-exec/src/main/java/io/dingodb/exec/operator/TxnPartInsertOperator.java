/*
 * Copyright 2021 DataCanvas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dingodb.exec.operator;

import io.dingodb.common.CommonId;
import io.dingodb.common.partition.PartitionDefinition;
import io.dingodb.common.store.KeyValue;
import io.dingodb.common.type.DingoType;
import io.dingodb.common.util.ByteArrayUtils;
import io.dingodb.common.util.Optional;
import io.dingodb.exec.Services;
import io.dingodb.exec.converter.ValueConverter;
import io.dingodb.exec.dag.Vertex;
import io.dingodb.exec.operator.params.TxnPartInsertParam;
import io.dingodb.partition.DingoPartitionServiceProvider;
import io.dingodb.partition.PartitionService;
import io.dingodb.store.api.StoreInstance;

import static io.dingodb.common.util.NoBreakFunctions.wrap;

public class TxnPartInsertOperator extends PartModifyOperator {
    public static final TxnPartInsertOperator INSTANCE = new TxnPartInsertOperator();

    private TxnPartInsertOperator() {
    }

    @Override
    protected boolean pushTuple(Object[] tuple, Vertex vertex) {
        TxnPartInsertParam param = vertex.getParam();
        DingoType schema = param.getSchema();
        Object[] newTuple = (Object[]) schema.convertFrom(tuple, ValueConverter.INSTANCE);
        KeyValue keyValue = wrap(param.getCodec()::encode).apply(newTuple);
        CommonId partId = PartitionService.getService(
                Optional.ofNullable(param.getTableDefinition().getPartDefinition())
                    .map(PartitionDefinition::getFuncName)
                    .orElse(DingoPartitionServiceProvider.RANGE_FUNC_NAME))
            .calcPartId(keyValue.getKey(), param.getDistributions()); // TODO Set flag in front of the byte key
        StoreInstance store = Services.LOCAL_STORE.getInstance(param.getTableId(), partId);
        if (store.insert(keyValue)) {
            param.inc();
        }
        return true;
    }
}