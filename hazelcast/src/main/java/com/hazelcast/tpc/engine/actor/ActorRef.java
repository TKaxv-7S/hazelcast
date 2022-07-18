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

package com.hazelcast.tpc.engine.actor;

/**
 * A reference to some {@link Actor}.
 * <p>
 * All communication with the actor is done using its reference.
 */
public abstract class ActorRef<M> {

    /**
     * Sends the message to the actor.
     *
     * @param message the message
     * @throws NullPointerException when message is null.
     */
    public abstract void send(M message);
}
