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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Iterators;
import io.dingodb.common.CommonId;
import io.dingodb.common.type.DingoType;
import io.dingodb.common.type.TupleMapping;
import io.dingodb.common.type.converter.JsonConverter;
import io.dingodb.exec.expr.RtExprWithType;
import io.dingodb.expr.runtime.TupleEvalContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

@Slf4j
@JsonTypeName("get")
@JsonPropertyOrder({"table", "part", "schema", "keyMapping", "keys", "selection", "output"})
public final class GetByKeysOperator extends PartIteratorSourceOperator {
    private final List<Object[]> keyTuples;
    @JsonProperty("filter")
    private final RtExprWithType filter;
    @JsonProperty("selection")
    private final TupleMapping selection;

    public GetByKeysOperator(
        CommonId tableId,
        Object partId,
        DingoType schema,
        TupleMapping keyMapping,
        @Nonnull List<Object[]> keyTuples,
        RtExprWithType filter,
        TupleMapping selection
    ) {
        super(tableId, partId, schema, keyMapping);
        this.keyTuples = keyTuples;
        this.filter = filter;
        this.selection = selection;
    }

    @Nonnull
    @JsonCreator
    public static GetByKeysOperator fromJson(
        @JsonProperty("table") CommonId tableId,
        @JsonProperty("part") Object partId,
        @JsonProperty("schema") DingoType schema,
        @JsonProperty("keyMapping") TupleMapping keyMapping,
        @JsonDeserialize(using = TuplesDeserializer.class)
        @Nonnull @JsonProperty("keys") JsonNode jsonNode,
        @JsonProperty("filter") RtExprWithType filter,
        @JsonProperty("selection") TupleMapping selection
    ) {
        return new GetByKeysOperator(
            tableId,
            partId,
            schema,
            keyMapping,
            TuplesDeserializer.convertBySchema(jsonNode, schema.select(keyMapping)),
            filter,
            selection
        );
    }

    // This method is only used by json serialization.
    @JsonProperty("keys")
    public List<Object[]> getJsonKeyTuples() {
        return keyTuples.stream()
            .map(i -> (Object[]) schema.select(keyMapping).convertTo(i, JsonConverter.INSTANCE))
            .collect(Collectors.toList());
    }

    @Override
    public void init() {
        super.init();
        Iterator<Object[]> iterator = null;
        if (keyTuples.size() == 1) {
            for (Object[] keyTuple : keyTuples) {
                Object[] tuple = part.getByKey(keyTuple);
                if (tuple != null) {
                    iterator = Iterators.singletonIterator(tuple);
                }
                break;
            }
        } else if (keyTuples.size() != 0) {
            iterator = keyTuples.stream()
                .map(part::getByKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                .iterator();
        }
        if (iterator != null) {
            if (filter != null) {
                filter.compileIn(schema);
                iterator = Iterators.filter(
                    iterator,
                    tuple -> (boolean) filter.eval(new TupleEvalContext(tuple))
                );
            }
            if (selection != null) {
                this.iterator = Iterators.transform(iterator, selection::revMap);
            } else {
                this.iterator = iterator;
            }
            return;
        }
        this.iterator = Iterators.forArray();
    }
}
