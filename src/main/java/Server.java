import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class Server {
    private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
    private Server() { }

    public static void main(String... args) throws Exception {
        class Http3ServerHandler extends Http3RequestStreamInboundHandler {
            @Override
            protected void channelRead(
                    ChannelHandlerContext ctx, Http3HeadersFrame frame, boolean isLast) {
                if (isLast) {
                    writeResponse(ctx);
                }
                ReferenceCountUtil.release(frame);
            }

            @Override
            protected void channelRead(
                    ChannelHandlerContext ctx, Http3DataFrame frame, boolean isLast) {
                if (isLast) {
                    writeResponse(ctx);
                }
                ReferenceCountUtil.release(frame);
            }

            private void writeResponse(ChannelHandlerContext ctx) {
                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                headersFrame.headers().status("200");
                headersFrame.headers().set("server", "netty");
                ctx.write(headersFrame);
                ctx.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(CONTENT)))
                        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            }
        }

        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
                .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // Called for each connection
                        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                new ChannelInitializer<QuicStreamChannel>() {
                                    // Called for each request-stream,
                                    @Override
                                    protected void initChannel(QuicStreamChannel ch) {
                                        ch.pipeline().addLast(new Http3ServerHandler());
                                    }
                                }));
                    }
                }).build();

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}