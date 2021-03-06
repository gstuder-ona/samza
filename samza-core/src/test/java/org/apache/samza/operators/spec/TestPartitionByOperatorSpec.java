/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.samza.operators.spec;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.samza.application.descriptors.StreamApplicationDescriptorImpl;
import org.apache.samza.config.Config;
import org.apache.samza.config.JobConfig;
import org.apache.samza.system.descriptors.GenericInputDescriptor;
import org.apache.samza.system.descriptors.GenericSystemDescriptor;
import org.apache.samza.config.MapConfig;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.OperatorSpecGraph;
import org.apache.samza.operators.Scheduler;
import org.apache.samza.operators.functions.MapFunction;
import org.apache.samza.operators.functions.ScheduledFunction;
import org.apache.samza.operators.functions.WatermarkFunction;
import org.apache.samza.serializers.KVSerde;
import org.apache.samza.serializers.NoOpSerde;
import org.apache.samza.serializers.Serde;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


/**
 * Unit tests for partitionBy operator
 */
public class TestPartitionByOperatorSpec {
  private final String testJobName = "testJob";
  private final String testJobId = "1";
  private final String testRepartitionedStreamName = "parByKey";
  private final GenericInputDescriptor testInputDescriptor =
      new GenericSystemDescriptor("mockSystem", "mockFactoryClassName")
          .getInputDescriptor("test-input-1", mock(Serde.class));

  @Test
  public void testPartitionBy() {
    MapFunction<Object, String> keyFn = m -> m.toString();
    MapFunction<Object, Object> valueFn = m -> m;
    KVSerde<Object, Object> partitionBySerde = KVSerde.of(new NoOpSerde<>(), new NoOpSerde<>());
    StreamApplicationDescriptorImpl streamAppDesc = new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(keyFn, valueFn, partitionBySerde, testRepartitionedStreamName);
    }, getConfig());
    assertEquals(2, streamAppDesc.getInputOperators().size());
    Map<String, InputOperatorSpec> inputOpSpecs = streamAppDesc.getInputOperators();
    assertTrue(inputOpSpecs.keySet().contains(String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName)));
    InputOperatorSpec inputOpSpec = inputOpSpecs.get(String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName));
    assertEquals(String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName), inputOpSpec.getStreamId());
    assertTrue(inputOpSpec.getKeySerde() instanceof NoOpSerde);
    assertTrue(inputOpSpec.getValueSerde() instanceof NoOpSerde);
    assertTrue(inputOpSpec.isKeyed());
    assertNull(inputOpSpec.getScheduledFn());
    assertNull(inputOpSpec.getWatermarkFn());
    InputOperatorSpec originInputSpec = inputOpSpecs.get(testInputDescriptor.getStreamId());
    assertTrue(originInputSpec.getRegisteredOperatorSpecs().toArray()[0] instanceof PartitionByOperatorSpec);
    PartitionByOperatorSpec reparOpSpec  = (PartitionByOperatorSpec) originInputSpec.getRegisteredOperatorSpecs().toArray()[0];
    assertEquals(reparOpSpec.getOpId(), String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName));
    assertEquals(reparOpSpec.getKeyFunction(), keyFn);
    assertEquals(reparOpSpec.getValueFunction(), valueFn);
    assertEquals(reparOpSpec.getOutputStream().getStreamId(), reparOpSpec.getOpId());
    assertNull(reparOpSpec.getScheduledFn());
    assertNull(reparOpSpec.getWatermarkFn());
  }

  @Test
  public void testPartitionByWithNoSerde() {
    MapFunction<Object, String> keyFn = m -> m.toString();
    MapFunction<Object, Object> valueFn = m -> m;
    StreamApplicationDescriptorImpl streamAppDesc = new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(keyFn, valueFn, mock(KVSerde.class), testRepartitionedStreamName);
    }, getConfig());
    InputOperatorSpec inputOpSpec = streamAppDesc.getInputOperators().get(
        String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName));
    assertNotNull(inputOpSpec);
    assertNull(inputOpSpec.getKeySerde());
    assertNull(inputOpSpec.getValueSerde());
    assertTrue(inputOpSpec.isKeyed());
    assertNull(inputOpSpec.getScheduledFn());
    assertNull(inputOpSpec.getWatermarkFn());
    InputOperatorSpec originInputSpec = streamAppDesc.getInputOperators().get(testInputDescriptor.getStreamId());
    assertTrue(originInputSpec.getRegisteredOperatorSpecs().toArray()[0] instanceof PartitionByOperatorSpec);
    PartitionByOperatorSpec reparOpSpec  = (PartitionByOperatorSpec) originInputSpec.getRegisteredOperatorSpecs().toArray()[0];
    assertEquals(reparOpSpec.getOpId(), String.format("%s-%s-partition_by-%s", testJobName, testJobId, testRepartitionedStreamName));
    assertEquals(reparOpSpec.getKeyFunction(), keyFn);
    assertEquals(reparOpSpec.getValueFunction(), valueFn);
    assertEquals(reparOpSpec.getOutputStream().getStreamId(), reparOpSpec.getOpId());
    assertNull(reparOpSpec.getScheduledFn());
    assertNull(reparOpSpec.getWatermarkFn());
  }

  @Test
  public void testCopy() {
    StreamApplicationDescriptorImpl streamAppDesc = new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(m -> m.toString(), m -> m, mock(KVSerde.class), testRepartitionedStreamName);
    }, getConfig());
    OperatorSpecGraph specGraph = streamAppDesc.getOperatorSpecGraph();
    OperatorSpecGraph clonedGraph = specGraph.clone();
    OperatorSpecTestUtils.assertClonedGraph(specGraph, clonedGraph);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testScheduledFunctionAsKeyFn() {
    ScheduledMapFn keyFn = new ScheduledMapFn();
    new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream<Object> inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(keyFn, m -> m, mock(KVSerde.class), "parByKey");
    }, getConfig());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWatermarkFunctionAsKeyFn() {
    WatermarkMapFn keyFn = new WatermarkMapFn();
    new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream<Object> inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(keyFn, m -> m, mock(KVSerde.class), "parByKey");
    }, getConfig());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testScheduledFunctionAsValueFn() {
    ScheduledMapFn valueFn = new ScheduledMapFn();
    new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream<Object> inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(m -> m.toString(), valueFn, mock(KVSerde.class), "parByKey");
    }, getConfig());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWatermarkFunctionAsValueFn() {
    WatermarkMapFn valueFn = new WatermarkMapFn();
    new StreamApplicationDescriptorImpl(appDesc -> {
      MessageStream<Object> inputStream = appDesc.getInputStream(testInputDescriptor);
      inputStream.partitionBy(m -> m.toString(), valueFn, mock(KVSerde.class), "parByKey");
    }, getConfig());
  }

  private Config getConfig() {
    HashMap<String, String> configMap = new HashMap<>();
    configMap.put(JobConfig.JOB_NAME, testJobName);
    configMap.put(JobConfig.JOB_ID, testJobId);
    return new MapConfig(configMap);
  }

  class ScheduledMapFn implements MapFunction<Object, String>, ScheduledFunction<String, Object> {
    @Override
    public String apply(Object message) {
      return message.toString();
    }

    @Override
    public void schedule(Scheduler<String> scheduler) {

    }

    @Override
    public Collection<Object> onCallback(String key, long timestamp) {
      return null;
    }
  }

  class WatermarkMapFn implements MapFunction<Object, String>, WatermarkFunction<Object> {

    @Override
    public String apply(Object message) {
      return message.toString();
    }

    @Override
    public Collection<Object> processWatermark(long watermark) {
      return null;
    }

    @Override
    public Long getOutputWatermark() {
      return null;
    }
  }
}
