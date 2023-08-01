/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.internal.tpcengine.iouring;


import com.hazelcast.internal.tpcengine.Eventloop;
import com.hazelcast.internal.tpcengine.Reactor;
import com.hazelcast.internal.tpcengine.ReactorType;
import com.hazelcast.internal.tpcengine.net.AbstractAsyncSocket;
import com.hazelcast.internal.tpcengine.net.AsyncServerSocket;
import com.hazelcast.internal.tpcengine.net.AsyncSocket;

import static com.hazelcast.internal.tpcengine.util.Preconditions.checkNotNegative;
import static com.hazelcast.internal.tpcengine.util.Preconditions.checkPositive;

/**
 * io_uring {@link Reactor} implementation.
 *
 * <p>
 * Good read:
 * https://unixism.net/2020/04/io-uring-by-example-part-3-a-web-server-with-io-uring/
 * <p>
 * Another example (blocking socket)
 * https://github.com/ddeka0/AsyncIO/blob/master/src/asyncServer.cpp
 * <p>
 * no syscalls:
 * https://wjwh.eu/posts/2021-10-01-no-syscall-server-iouring.html
 */
public final class IOUringReactor extends Reactor {

    private final EventFd eventFd;

    private IOUringReactor(Builder builder) {
        super(builder);
        this.eventFd = ((IOUringEventloop) eventloop()).eventfd;
    }

    @Override
    protected Eventloop newEventloop(Reactor.Builder builder) {
        IOUringEventloop.Builder eventloopBuilder = new IOUringEventloop.Builder();
        eventloopBuilder.reactorBuilder = builder;
        eventloopBuilder.reactor = this;
        return eventloopBuilder.build();
    }

    @Override
    public AsyncSocket.Builder newAsyncSocketBuilder() {
        verifyRunning();

        IOUringAsyncSocket.Builder socketBuilder = new IOUringAsyncSocket.Builder(null);
        socketBuilder.networkScheduler = eventloop.networkScheduler();
        socketBuilder.reactor = this;
        return socketBuilder;
    }

    @Override
    public AsyncSocket.Builder newAsyncSocketBuilder(AbstractAsyncSocket.AcceptRequest acceptRequest) {
        verifyRunning();

        IOUringAsyncSocket.Builder socketBuilder = new IOUringAsyncSocket.Builder(
                (IOUringAcceptRequest) acceptRequest);
        socketBuilder.reactor = this;
        socketBuilder.networkScheduler = eventloop.networkScheduler();
        return socketBuilder;
    }

    @Override
    public AsyncServerSocket.Builder newAsyncServerSocketBuilder() {
        verifyRunning();

        IOUringAsyncServerSocket.Builder serverBuilder = new IOUringAsyncServerSocket.Builder();
        serverBuilder.reactor = this;
        return serverBuilder;
    }

    @Override
    public void wakeup() {
        if (spin || Thread.currentThread() == eventloopThread) {
            return;
        }

        if (wakeupNeeded.get() && wakeupNeeded.compareAndSet(true, false)) {
            eventFd.write(1L);
        }
    }

    /**
     * The Builder for the {@link IOUringReactor}.
     */
    @SuppressWarnings({"checkstyle:VisibilityModifier"})
    public static class Builder extends Reactor.Builder {

        public static final int DEFAULT_ENTRIES = 8192;

        /**
         * Sets the setup flags for the io_uring instance. See the IoUring.IORING_SETUP
         * constants.
         */
        public int setupFlags;

        /**
         * The number of entries for the io_uring instance.
         * <p/>
         * For more information see:
         * https://man7.org/linux/man-pages//man2/io_uring_enter.2.html
         */
        public int entries = DEFAULT_ENTRIES;

        /**
         * Configures if the file descriptor of the io_uring instance should be
         * registered. The purpose of registration it to speed up io_uring_enter.
         * <p/>
         * For more information see:
         * https://man7.org/linux/man-pages/man3/io_uring_register_ring_fd.3.html
         * <p/>
         * This is an ultra power feature and should probably not be used by anyone.
         * You can only have 16 io_uring instances with registered ring file
         * descriptor. If you create more, you will run into a 'Device or resource busy'
         * exception.
         */
        public boolean registerRing;

        public Builder() {
            super(ReactorType.IOURING);
        }

        @Override
        protected Reactor doBuild() {
            return new IOUringReactor(this);
        }

        @Override
        protected void conclude() {
            super.conclude();

            checkNotNegative(setupFlags, "setupFlags");
            checkPositive(entries, "entries");
        }
    }
}

