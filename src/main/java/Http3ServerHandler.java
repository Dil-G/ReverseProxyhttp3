import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class Http3ServerHandler extends Http3RequestStreamInboundHandler {

    private QuicChannel quicChannel;
    private  QuicStreamChannel streamChannel;

    private volatile Channel channel;

    private ChannelFuture channelFuture;

    public void channelActive(ChannelHandlerContext ctx) throws InterruptedException, ExecutionException {
        System.out.println("ServerHandler channelActive");
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        final Channel inboundChannel = ctx.channel();


            QuicSslContext context = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols(Http3.supportedApplicationProtocols()).build();
            ChannelHandler codec = Http3.newQuicClientCodecBuilder()
                    .sslContext(context)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build();

            Bootstrap bootstrap = new Bootstrap();
            channel = bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0).sync().channel();

            quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new Http3ClientConnectionHandler())
                    .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9090))
                    .connect()
                    .get();
            streamChannel = Http3.newRequestStream(quicChannel,
                    new ClientHandler(inboundChannel)).sync().getNow();

    }


    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame, boolean isLast) throws InterruptedException {
        System.out.println("ServerHandler Header Channel Read");
            streamChannel.writeAndFlush(frame)
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame, boolean isLast) {
        System.out.println("ServerHandler Data Channel Read");
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (streamChannel != null) {
            System.out.println("Server Channel Inactive");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
