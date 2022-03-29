package io.netty.incubator.channel.uring;

import com.hazelcast.cluster.Address;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.internal.nio.Packet;
import com.hazelcast.internal.serialization.impl.ByteArrayObjectDataInput;
import com.hazelcast.internal.serialization.impl.ByteArrayObjectDataOutput;
import com.hazelcast.internal.server.ServerConnection;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.impl.reactor.*;
import com.hazelcast.table.impl.SelectByKeyOperation;
import com.hazelcast.table.impl.UpsertOperation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.PlatformDependent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hazelcast.internal.nio.IOUtil.compactOrClear;
import static com.hazelcast.spi.impl.reactor.Op.RUN_CODE_DONE;
import static com.hazelcast.spi.impl.reactor.Op.RUN_CODE_FOO;
import static com.hazelcast.spi.impl.reactor.OpCodes.TABLE_SELECT_BY_KEY;
import static com.hazelcast.spi.impl.reactor.OpCodes.TABLE_UPSERT;
import static io.netty.incubator.channel.uring.Native.DEFAULT_IOSEQ_ASYNC_THRESHOLD;
import static io.netty.incubator.channel.uring.Native.DEFAULT_RING_SIZE;
import static io.netty.incubator.channel.uring.Native.IORING_OP_ACCEPT;
import static io.netty.incubator.channel.uring.Native.IORING_OP_READ;
import static io.netty.incubator.channel.uring.Native.IORING_OP_WRITE;
import static java.nio.ByteOrder.BIG_ENDIAN;


/**
 * To build io uring:
 * <p>
 * sudo yum install autoconf
 * sudo yum install automake
 * sudo yum install libtool
 * <p>
 * Good read:
 * https://unixism.net/2020/04/io-uring-by-example-part-3-a-web-server-with-io-uring/
 * <p>
 * Another example (blocking socket)
 * https://github.com/ddeka0/AsyncIO/blob/master/src/asyncServer.cpp
 * <p>
 * no syscalls:
 * https://wjwh.eu/posts/2021-10-01-no-syscall-server-iouring.html
 * <p>
 * Error codes:
 * https://www.thegeekstuff.com/2010/10/linux-error-codes/
 * <p>
 * <p>
 * https://github.com/torvalds/linux/blob/master/include/uapi/linux/io_uring.h
 * IORING_OP_NOP               0
 * IORING_OP_READV             1
 * IORING_OP_WRITEV            2
 * IORING_OP_FSYNC             3
 * IORING_OP_READ_FIXED        4
 * IORING_OP_WRITE_FIXED       5
 * IORING_OP_POLL_ADD          6
 * IORING_OP_POLL_REMOVE       7
 * IORING_OP_SYNC_FILE_RANGE   8
 * IORING_OP_SENDMSG           9
 * IORING_OP_RECVMSG           10
 * IORING_OP_TIMEOUT,          11
 * IORING_OP_TIMEOUT_REMOVE,   12
 * IORING_OP_ACCEPT,           13
 * IORING_OP_ASYNC_CANCEL,     14
 * IORING_OP_LINK_TIMEOUT,     15
 * IORING_OP_CONNECT,          16
 * IORING_OP_FALLOCATE,        17
 * IORING_OP_OPENAT,
 * IORING_OP_CLOSE,
 * IORING_OP_FILES_UPDATE,
 * IORING_OP_STATX,
 * IORING_OP_READ,
 * IORING_OP_WRITE,
 * IORING_OP_FADVISE,
 * IORING_OP_MADVISE,
 * IORING_OP_SEND,
 * IORING_OP_RECV,
 * IORING_OP_OPENAT2,
 * IORING_OP_EPOLL_CTL,
 * IORING_OP_SPLICE,
 * IORING_OP_PROVIDE_BUFFERS,
 * IORING_OP_REMOVE_BUFFERS,
 * IORING_OP_TEE,
 * IORING_OP_SHUTDOWN,
 * IORING_OP_RENAMEAT,
 * IORING_OP_UNLINKAT,
 * IORING_OP_MKDIRAT,
 * IORING_OP_SYMLINKAT,
 * IORING_OP_LINKAT,
 * IORING_OP_MSG_RING,
 */
public class IO_UringReactor extends Reactor implements IOUringCompletionQueueCallback {

//    static {
//        System.out.println("IORING_OP_POLL_ADD:" + IORING_OP_POLL_ADD);
//        System.out.println("IORING_OP_TIMEOUT:" + IORING_OP_TIMEOUT);
//        System.out.println("IORING_OP_ACCEPT:" + IORING_OP_ACCEPT);
//        System.out.println("IORING_OP_READ:" + IORING_OP_READ);
//        System.out.println("IORING_OP_WRITE:" + IORING_OP_WRITE);
//        System.out.println("IORING_OP_POLL_REMOVE:" + IORING_OP_POLL_REMOVE);
//        System.out.println("IORING_OP_CONNECT:" + IORING_OP_CONNECT);
//        System.out.println("IORING_OP_CLOSE:" + IORING_OP_CLOSE);
//        System.out.println("IORING_OP_WRITEV:" + IORING_OP_WRITEV);
//        System.out.println("IORING_OP_SENDMSG:" + IORING_OP_SENDMSG);
//        System.out.println("IORING_OP_RECVMSG:" + IORING_OP_RECVMSG);
//    }

    private final boolean spin;
    public final ConcurrentLinkedQueue taskQueue = new ConcurrentLinkedQueue();
    private final RingBuffer ringBuffer;
    private final FileDescriptor eventfd;
    private LinuxSocket serverSocket;
    private final IOUringSubmissionQueue sq;
    private final IOUringCompletionQueue cq;
    private final AtomicBoolean wakeupNeeded = new AtomicBoolean();
    private long nextPrint = System.currentTimeMillis() + 1000;
    private ByteBuffer acceptedAddressMemory;
    private long acceptedAddressMemoryAddress;
    private ByteBuffer acceptedAddressLengthMemory;
    private long acceptedAddressLengthMemoryAddress;
    private final long eventfdReadBuf = PlatformDependent.allocateMemory(8);
    private final IntObjectMap<IO_UringChannel> channels = new IntObjectHashMap<>(4096);
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    //private final IOUringRecvByteAllocatorHandle recvByteAllocatorHandle;
    private final PooledByteBufAllocator allocator = new PooledByteBufAllocator(true);

    public IO_UringReactor(ReactorFrontEnd frontend, Address thisAddress, int port, boolean spin) {
        super(frontend, thisAddress, port, "IOUringReactor:[" + thisAddress.getHost() + ":" + thisAddress.getPort() + "]:" + port);
        this.spin = spin;
        this.ringBuffer = Native.createRingBuffer(DEFAULT_RING_SIZE, DEFAULT_IOSEQ_ASYNC_THRESHOLD);
        this.sq = ringBuffer.ioUringSubmissionQueue();
        this.cq = ringBuffer.ioUringCompletionQueue();
        this.eventfd = Native.newBlockingEventFd();
        //this.recvByteAllocatorHandle = new IOUringRecvByteAllocatorHandle((RecvByteBufAllocator.ExtendedHandle) new ServerChannelRecvByteBufAllocator().newHandle());

        this.acceptedAddressMemory = Buffer.allocateDirectWithNativeOrder(Native.SIZEOF_SOCKADDR_STORAGE);
        this.acceptedAddressMemoryAddress = Buffer.memoryAddress(acceptedAddressMemory);
        this.acceptedAddressLengthMemory = Buffer.allocateDirectWithNativeOrder(Long.BYTES);
        // Needs to be initialized to the size of acceptedAddressMemory.
        // See https://man7.org/linux/man-pages/man2/accept.2.html
        this.acceptedAddressLengthMemory.putLong(0, Native.SIZEOF_SOCKADDR_STORAGE);
        this.acceptedAddressLengthMemoryAddress = Buffer.memoryAddress(acceptedAddressLengthMemory);
    }

    @Override
    public void wakeup() {
        if (spin || Thread.currentThread() == this) {
            return;
        }

        if (wakeupNeeded.get() && wakeupNeeded.compareAndSet(true, false)) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventfd.intValue(), 1L);
        }
    }

    @Override
    public void enqueue(Request request) {
        taskQueue.add(request);
        wakeup();
    }

    @Override
    public Future<Channel> enqueue(SocketAddress address, Connection connection) {
        logger.info("Connect to " + address);

        ConnectRequest connectRequest = new ConnectRequest();
        connectRequest.address = address;
        connectRequest.connection = connection;
        connectRequest.future = new CompletableFuture<>();
        taskQueue.add(connectRequest);

        wakeup();

        return connectRequest.future;
    }

    private boolean setupServerSocket() {
        InetSocketAddress serverAddress = null;
        try {
            this.serverSocket = LinuxSocket.newSocketStream(false);
            this.serverSocket.setBlocking();
            this.serverSocket.setReuseAddress(true);
            System.out.println(getName() + " serverSocket.fd:" + serverSocket.intValue());

            serverAddress = new InetSocketAddress(thisAddress.getInetAddress(), port);
            //serverSocket.setTcpQuickAck(false);
            //serverSocket.setTcpNoDelay(true);
            serverSocket.bind(serverAddress);
            System.out.println(getName() + " Bind success " + serverAddress);
            serverSocket.listen(10);
            System.out.println(getName() + " Listening on " + serverAddress);
            return true;
        } catch (IOException e) {
            logger.severe(getName() + " Could not bind to " + serverAddress);
        }
        return false;
    }

    @NotNull
    private IO_UringChannel newChannel(LinuxSocket socket, Connection connection) {
        IO_UringChannel channel = new IO_UringChannel();
        channel.socket = socket;
        channel.localAddress = socket.localAddress();
        channel.reactor = this;
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        channel.readBuff = allocator.directBuffer(1024);
        channel.writeBuff = allocator.directBuffer(1024);
        channel.readBuffer = ByteBuffer.allocate(2000);
        channel.connection = connection;
        return channel;
    }

    @Override
    public void executeRun() {
        try {
            if (setupServerSocket()) {
                eventLoop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void eventLoop() throws Exception {
        sq_addEventRead();
        sq_addAccept();

        // not needed
        sq.submit();

        while (!frontend.shuttingdown) {
            // Check there were any completion events to process
            if (!cq.hasCompletions()) {
                //System.out.println(getName() + " submitAndWait:started");
                wakeupNeeded.set(true);
                sq.submitAndWait();
                wakeupNeeded.set(false);
                //System.out.println(getName() + " submitAndWait:completed");
            } else {
                int processed = cq.process(this);
                if (processed > 0) {
                    System.out.println(getName() + " processed " + processed);
                }
            }

            processTasks();
        }
    }

    private void sq_addEventRead() {
        sq.addEventFdRead(eventfd.intValue(), eventfdReadBuf, 0, 8, (short) 0);
    }

    private void sq_addAccept() {
        sq.addAccept(serverSocket.intValue(), acceptedAddressMemoryAddress, acceptedAddressLengthMemoryAddress, (short) 0);
    }

    private void sq_addRead(IO_UringChannel channel) {
        ByteBuf b = channel.readBuff;
        System.out.println("sq_addRead writerIndex:" + b.writerIndex() + " capacity:" + b.capacity());
        sq.addRead(channel.socket.intValue(), b.memoryAddress(), b.writerIndex(), b.capacity(), (short) 0);
    }

    @Override
    public void handle(int fd, int res, int flags, byte op, short data) {
        //System.out.println(getName() + " handle called: opcode:" + op);

        if (op == IORING_OP_READ) {
            handle_IORING_OP_READ(fd, res, flags);
        } else if (op == IORING_OP_WRITE) {
            handle_IORING_OP_WRITE(op);
        } else if (op == IORING_OP_ACCEPT) {
            handle_IORING_OP_ACCEPT(fd, res);
        } else {
            System.out.println(this + " handle Unknown opcode:" + op);
        }
    }

    private void handle_IORING_OP_ACCEPT(int fd, int res) {
        sq_addAccept();

        System.out.println(getName() + " handle IORING_OP_ACCEPT fd:" + fd + " serverFd:" + serverSocket.intValue() + "res:" + res);

        if (res >= 0) {
            SocketAddress address = SockaddrIn.readIPv4(acceptedAddressMemoryAddress, inet4AddressArray);
            System.out.println(this + " new connected accepted: " + address);

            IO_UringChannel channel = newChannel(new LinuxSocket(res), null);
            channel.remoteAddress = address;
            channels.put(res, channel);
            sq_addRead(channel);
        }
    }

    private void handle_IORING_OP_WRITE(byte op) {
        System.out.println(getName() + " handle called: opcode:" + op);
    }

    private void handle_IORING_OP_READ(int fd, int res, int flags) {
        // res is the number of bytes read

        if (fd == eventfd.intValue()) {
            //System.out.println(getName() + " handle IORING_OP_READ from eventFd res:" + res);
            sq_addEventRead();
            return;
        }

        System.out.println(getName() + " handle IORING_OP_READ from fd:" + fd + " res:" + res + " flags:" + flags);

        IO_UringChannel channel = channels.get(fd);
        // we need to update the writerIndex; not done automatically.
        //todo: we need to deal with res=0 and res<0
        int oldLimit = channel.readBuffer.limit();
        channel.readBuffer.limit(res);
        channel.readBuff.writerIndex(channel.readBuff.writerIndex() + res);
        System.out.println(IOUtil.toDebugString("before channel.readBuffer", channel.readBuffer));
        channel.readBuff.readBytes(channel.readBuffer);
        channel.readBuffer.limit(oldLimit);
        System.out.println(IOUtil.toDebugString("after channel.readBuffer", channel.readBuffer));
        sq_addRead(channel);

        channel.readBuffer.flip();
        for (; ; ) {
            Packet packet = channel.packetReader.readFrom(channel.readBuffer);
            System.out.println(this + " ------------------------- read packet: " + packet);
            if (packet == null) {
                break;
            }

            channel.packetsRead++;
            packet.setConn((ServerConnection) channel.connection);
            packet.channel = channel;
            process(packet);
        }
        compactOrClear(channel.readBuffer);
    }


    private void processTasks() {
        for (; ; ) {
            Object item = taskQueue.poll();
            if (item == null) {
                return;
            }

            if (item instanceof IO_UringChannel) {
                process((IO_UringChannel) item);
            } else if (item instanceof ConnectRequest) {
                process((ConnectRequest) item);
            } else if (item instanceof Op) {
                process((Op) item);
            } else if (item instanceof Request) {
                process((Request) item);
            } else {
                throw new RuntimeException("Unrecognized type:" + item.getClass());
            }
        }
    }

    private void process(IO_UringChannel channel) {
        System.out.println(getName() + " process channel " + channel.remoteAddress);

        ByteBuf buf = channel.writeBuff;
        int written = 0;
        for (; ; ) {
            ByteBuffer buffer = channel.next();
            if (buffer == null) {
                break;
            }
            written += buffer.remaining();
            buf.writeBytes(buffer);
        }

        System.out.println(this + " process readable bytes: " + buf.readableBytes() + " written from buffer:" + written);
        // todo: we only keep adding to the buffer.
        sq.addWrite(channel.socket.intValue(), buf.memoryAddress(), buf.readerIndex(), buf.writerIndex(), (short) 0);
    }

    private void process(ConnectRequest request) {
        try {
            SocketAddress address = request.address;
            System.out.println(getName() + " connectRequest to address:" + address);

            LinuxSocket socket = LinuxSocket.newSocketStream(false);
            socket.setBlocking();
            socket.setTcpNoDelay(true);
            socket.setTcpQuickAck(false);

            if (!socket.connect(request.address)) {
                request.future.completeExceptionally(new IOException("Could not connect to " + request.address));
                return;
            }
            logger.info(getName() + "Socket connected to " + address);

            IO_UringChannel channel = newChannel(socket, request.connection);
            channel.remoteAddress = request.address;
            channels.put(socket.intValue(), channel);
            request.future.complete(channel);
            sq_addRead(channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(Packet packet) {
        System.out.println(this + " ----------------- process packet: " + packet);
        try {
            if (packet.isFlagRaised(Packet.FLAG_OP_RESPONSE)) {
                frontend.handleResponse(packet);
            } else {
                byte[] bytes = packet.toByteArray();
                byte opcode = bytes[Packet.DATA_OFFSET];
                Op op = allocateOp(opcode);
                op.in.init(packet.toByteArray(), Packet.DATA_OFFSET + 1);
                process(op);

                //System.out.println("We need to send response to "+op.callId);
                ByteArrayObjectDataOutput out = op.out;
                ByteBuffer byteBuffer = ByteBuffer.wrap(out.toByteArray(), 0, out.position());
                System.out.println("writing " + out.position());

                // individual flush might not be smart
                packet.channel.writeAndFlush(byteBuffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // local call
    private void process(Request request) {
        //System.out.println("request: " + request);
        try {
            byte[] data = request.out.toByteArray();
            byte opcode = data[0];
            Op op = allocateOp(opcode);
            op.in.init(data, 1);
            process(op);
            request.invocation.completableFuture.complete(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(Op op) {
        try {
            long callId = op.in.readLong();
            op.callId = callId;
            //System.out.println("callId: "+callId);
            int runCode = op.run();
            switch (runCode) {
                case RUN_CODE_DONE:
                    free(op);
                    return;
                case RUN_CODE_FOO:
                    throw new RuntimeException();
                default:
                    throw new RuntimeException();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // use pool
    private Op allocateOp(int opcode) {
        Op op;
        switch (opcode) {
            case TABLE_UPSERT:
                op = new UpsertOperation();
                break;
            case TABLE_SELECT_BY_KEY:
                op = new SelectByKeyOperation();
                break;
            default://hack
                op = new UpsertOperation();
                //throw new RuntimeException("Unrecognized opcode:" + opcode);
        }
        op.in = new ByteArrayObjectDataInput(null, frontend.ss, BIG_ENDIAN);
        op.out = new ByteArrayObjectDataOutput(64, frontend.ss, BIG_ENDIAN);
        op.managers = frontend.managers;
        return op;
    }

    private void free(Op op) {
        op.cleanup();

        //we should return it to the pool.
    }
}
