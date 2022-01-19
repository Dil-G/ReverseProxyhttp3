import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;

public class ClientHandler extends Http3RequestStreamInboundHandler {

    private final Channel inboundChannel;

    public ClientHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx,
                               Http3HeadersFrame frame, boolean isLast) {
        System.out.println("ClientHandler header Channel Read");
        inboundChannel.writeAndFlush(frame).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    System.out.println("Client fail");
                    future.channel().close();
                }
            }
        });    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx,
                               Http3DataFrame frame, boolean isLast) {
        System.out.println("ClientHandler data Channel Read");
        inboundChannel.writeAndFlush(frame).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
//                    System.out.println("Client SUCCESS");
                    ctx.channel().read();
                } else {
                    System.out.println("Client fail");
                    future.channel().close();
                }
            }
        });
    }



    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("ClientHandler ChannelActive");
        ctx.read();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!inboundChannel.isActive()) {
            System.out.println("Client Channel Inactive");
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
