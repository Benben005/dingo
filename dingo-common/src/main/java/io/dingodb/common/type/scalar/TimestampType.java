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

package io.dingodb.common.type.scalar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dingodb.common.type.DataConverter;
import io.dingodb.common.type.NullType;
import io.dingodb.expr.runtime.TypeCode;
import io.dingodb.expr.runtime.utils.DateTimeUtils;
import io.dingodb.serial.schema.DingoSchema;
import io.dingodb.serial.schema.LongSchema;
import org.apache.avro.Schema;

import java.sql.Timestamp;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@JsonTypeName("timestamp")
public class TimestampType extends AbstractScalarType {
    @JsonCreator
    public TimestampType(@JsonProperty("nullable") boolean nullable) {
        super(TypeCode.TIMESTAMP, nullable);
    }

    @Override
    public TimestampType copy() {
        return new TimestampType(nullable);
    }

    @Override
    public DingoSchema toDingoSchema(int index) {
        return new LongSchema(index);
    }

    @Override
    public String format(@Nullable Object value) {
        return value != null
            ? DateTimeUtils.timestampFormat((Timestamp) value) + ":" + this
            : NullType.NULL.format(null);
    }

    @Override
    protected Object convertValueTo(@Nonnull Object value, @Nonnull DataConverter converter) {
        return converter.convert((Timestamp) value);
    }

    @Override
    protected Object convertValueFrom(@Nonnull Object value, @Nonnull DataConverter converter) {
        return converter.convertTimestampFrom(value);
    }


    @Override
    protected Schema.Type getAvroSchemaType() {
        return Schema.Type.LONG;
    }
}
