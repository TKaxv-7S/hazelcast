/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.mapreduce;

/**
 * <p>The LifecycleMapper interface is a more sophisticated version of {@link Mapper} normally used for complexer
 * algorithms with a need of initialization and finalization.</p>
 * <p>The behavior is the same as for {@link Mapper} but {@link #initialize(com.hazelcast.mapreduce.Context)} is called before calling
 * {@link #map(Object, Object, com.hazelcast.mapreduce.Context)} for the first time to prepare the mapper instance and maybe already
 * emit some values. After all mapping calls are finished {@link #finalized(com.hazelcast.mapreduce.Context)} is called and here is
 * also the possibility given to emit additional key-value pairs.</p>
 *
 * @param <KeyIn>    The type of key used in the {@link com.hazelcast.mapreduce.KeyValueSource}
 * @param <ValueIn>  The type of value used in the {@link com.hazelcast.mapreduce.KeyValueSource}
 * @param <KeyOut>   The key type for mapped results
 * @param <ValueOut> The value type for mapped results
 * @author noctarius
 */
public interface LifecycleMapper<KeyIn, ValueIn, KeyOut, ValueOut> extends Mapper<KeyIn, ValueIn, KeyOut, ValueOut> {

    /**
     * This method is called before the {@link #map(Object, Object, com.hazelcast.mapreduce.Context)} method is executed for every value and
     * can be used to initialize the internal state of the mapper or to emit a special value.
     *
     * @param context The {@link com.hazelcast.mapreduce.Context} to be used for emitting values.
     */
    void initialize(Context<KeyOut, ValueOut> context);

    /**
     * This method is called after the {@link #map(Object, Object, com.hazelcast.mapreduce.Context)} method is executed for every value and
     * can be used to finalize the internal state of the mapper or to emit a special value.
     *
     * @param context The {@link com.hazelcast.mapreduce.Context} to be used for emitting values.
     */
    void finalized(Context<KeyOut, ValueOut> context);

}
