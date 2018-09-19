/*
成都太阳高科技有限责任公司
http://www.suncd.com
*/
package com.asocket.manager.netty.client;

import com.asocket.manager.netty.demo.TimeClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NettyClient {

    @Autowired
    private NettyClientThread nettyClientThread;

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyClient.class);

    public void startClient() {
        String host = "192.168.0.105";
        int port = 7879;
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap client = new Bootstrap();
            client.group(workerGroup);
            client.channel(NioSocketChannel.class);
            client.option(ChannelOption.SO_KEEPALIVE, true);
            client.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new TimeClientHandler());
                }
            });

            // 启动客户端连接
            ChannelFuture f = client.connect(host, port).sync();
            LOGGER.info("客户端启动完成,主机IP:{},主机端口:{}", host, port);

            // 等待连接被关闭,执行如下
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            LOGGER.error("客户端初始化出现异常:", e);
        } finally {
            workerGroup.shutdownGracefully();
            LOGGER.warn("客户端将在10秒后重启");
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e1) {
                LOGGER.error(e1.getMessage());
            }
            // 重启客户端
            Thread client = new Thread(nettyClientThread);
            client.start();
        }
    }
}