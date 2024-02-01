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

package io.dingodb.calcite;

import io.dingodb.calcite.mock.MockMetaServiceProvider;
import io.dingodb.calcite.rel.DingoGetByKeys;
import io.dingodb.calcite.rel.DingoHashJoin;
import io.dingodb.calcite.rel.DingoRoot;
import io.dingodb.calcite.rel.DingoStreamingConverter;
import io.dingodb.calcite.rel.DingoTableModify;
import io.dingodb.calcite.rel.DingoValues;
import io.dingodb.calcite.rel.LogicalDingoRoot;
import io.dingodb.calcite.rel.LogicalDingoTableScan;
import io.dingodb.calcite.rel.dingo.DingoRelOp;
import io.dingodb.calcite.traits.DingoRelStreaming;
import io.dingodb.test.asserts.Assert;
import io.dingodb.test.asserts.AssertRelNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.Collect;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Properties;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class TestUpdate {
    private static DingoParserContext context;
    private DingoParser parser;

    @BeforeAll
    public static void setupAll() {
        MockMetaServiceProvider.init();
        Properties properties = new Properties();
        context = new DingoParserContext(MockMetaServiceProvider.SCHEMA_NAME, properties);
    }

    @BeforeEach
    public void setup() {
        // Create each time to clean the statistic info.
        parser = new DingoParser(context);
    }

    @Test
    public void testUpdate() throws SqlParseException {
        String sql = "update test set amount = 2.0 where id = 1";
        SqlNode sqlNode = parser.parse(sql);
        RelRoot relRoot = parser.convert(sqlNode);
        Assert.relNode(relRoot.rel)
            .isA(LogicalDingoRoot.class)
            .soleInput().isA(LogicalTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(LogicalProject.class)
            .soleInput().isA(LogicalFilter.class)
            .soleInput().isA(LogicalDingoTableScan.class);
        RelNode optimized = parser.optimize(relRoot.rel);
        Assert.relNode(optimized)
            .isA(DingoRoot.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoStreamingConverter.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(DingoStreamingConverter.class)
            .soleInput().isA(DingoRelOp.class)
            .soleInput().isA(DingoGetByKeys.class);
    }

    @Test
    public void testUpdate1() throws SqlParseException {
        String sql = "update test set amount = amount + 2.0 where id = 1";
        SqlNode sqlNode = parser.parse(sql);
        RelRoot relRoot = parser.convert(sqlNode);
        Assert.relNode(relRoot.rel)
            .isA(LogicalDingoRoot.class)
            .soleInput().isA(LogicalTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(LogicalProject.class)
            .soleInput().isA(LogicalFilter.class)
            .soleInput().isA(LogicalDingoTableScan.class);
        RelNode optimized = parser.optimize(relRoot.rel);
        Assert.relNode(optimized)
            .isA(DingoRoot.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoStreamingConverter.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(DingoStreamingConverter.class)
            .soleInput().isA(DingoRelOp.class)
            .soleInput().isA(DingoGetByKeys.class);
    }

    @Test
    public void testUpdateWithMultiset() throws SqlParseException {
        String sql = "update `table-with-array` set `set` = multiset[1, 2, 3] where id = 1";
        SqlNode sqlNode = parser.parse(sql);
        RelRoot relRoot = parser.convert(sqlNode);
        AssertRelNode assertJoin = Assert.relNode(relRoot.rel)
            .isA(LogicalDingoRoot.class)
            .soleInput().isA(LogicalTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(LogicalProject.class)
            .soleInput().isA(LogicalJoin.class);
        assertJoin.input(0).isA(LogicalFilter.class)
            .soleInput().isA(LogicalDingoTableScan.class);
        assertJoin.input(1).isA(Collect.class)
            .soleInput().isA(LogicalValues.class);
        RelNode optimized = parser.optimize(relRoot.rel);
        assertJoin = Assert.relNode(optimized)
            .isA(DingoRoot.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoStreamingConverter.class).streaming(DingoRelStreaming.ROOT)
            .soleInput().isA(DingoTableModify.class).prop("operation", TableModify.Operation.UPDATE)
            .soleInput().isA(DingoStreamingConverter.class)
            .soleInput().isA(DingoRelOp.class)
            .soleInput().isA(DingoHashJoin.class);
        assertJoin.input(0).isA(DingoStreamingConverter.class)
            .soleInput().isA(DingoGetByKeys.class);
        assertJoin.input(1).isA(DingoStreamingConverter.class)
            .soleInput().isA(DingoValues.class);
    }
}
