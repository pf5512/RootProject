package com.eboji.data.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eboji.commons.util.memcached.MemCacheClient;
import com.eboji.data.codec.MsgDecoder;
import com.eboji.data.codec.MsgEncoder;
import com.eboji.data.handler.DataServerHandler;
import com.eboji.data.handler.DataServerProcessor;
import com.eboji.data.util.RegisterCenterServerUtil;

public class DataServerListener {
	private static final Logger logger = LoggerFactory.getLogger(DataServerListener.class);
	
	private int port;
	
	private MemCacheClient memCacheClient = null;
	
	private DataServerProcessor dataProcessor = null;
	
	public DataServerListener(int port, MemCacheClient memCacheClient, 
			DataServerProcessor dataProcessor) throws Exception {
		this.port = port;
		this.memCacheClient = memCacheClient;
		this.dataProcessor = dataProcessor;
		
		bind();
	}
	
	protected void bind() throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		try {
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup);
			bootstrap.channel(NioServerSocketChannel.class);
			bootstrap.option(ChannelOption.SO_BACKLOG, 128);
			bootstrap.option(ChannelOption.TCP_NODELAY, true);
			bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
			
			bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipe = ch.pipeline();
					pipe.addLast(new MsgEncoder());
					pipe.addLast(new MsgDecoder());
					pipe.addLast(new DataServerHandler(memCacheClient, dataProcessor));
				}
			});
			
			ChannelFuture f = bootstrap.bind(port).sync();
			if(f.isSuccess()) {
				logger.info("Login Server listened in port " + this.port + " started.");
				
				RegisterCenterServerUtil.registerService();
			}
			
			f.channel().closeFuture().sync();
		} catch (Exception e) {
			throw e;
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
}
