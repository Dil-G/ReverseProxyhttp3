import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.*;

import java.util.concurrent.TimeUnit;

public final class Server {
    private Server() { }

    public static void main(String... args) throws Exception {

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
                        System.out.println("initchannel server");

                        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                new ChannelInitializer<QuicStreamChannel>() {

                                    @Override
                                    protected void initChannel(QuicStreamChannel ch) {
//                                        System.out.println("inti channel handle adding");
                                        ch.pipeline().addLast(new Http3ServerHandler());
                                    }
                                }));
                    }
                }).build();

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
//            System.out.println("bootstrap");

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .handler(codec)
                    .channel(NioDatagramChannel.class);

            ChannelFuture f = bootstrap.bind(8080).sync();
            f.channel().closeFuture().sync();

        } finally {
            group.shutdownGracefully();
        }
    }
}