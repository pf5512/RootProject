package com.eboji.agent.transfer.tcp.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.eboji.agent.bootstrap.Daemon;
import com.eboji.agent.handler.AgentServerClientMap;
import com.eboji.agent.transfer.tcp.ServerClientTransfer;
import com.eboji.agent.util.ConfigUtil;
import com.eboji.model.common.MsgType;
import com.eboji.model.constant.Constant;
import com.eboji.model.message.BaseMsg;
import com.eboji.model.message.ConnResMsg;
import com.eboji.model.message.LoginResMsg;
import com.eboji.model.message.PingMsg;
import com.eboji.model.message.RegisterResMsg;
import com.eboji.model.message.mj.MjCreateResMsg;

public class ServerClientHandler extends SimpleChannelInboundHandler<BaseMsg> {
	private static final Logger logger = LoggerFactory.getLogger(ServerClientHandler.class);
	
	//利用写空闲发送心跳检测消息
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		if(evt instanceof IdleStateEvent) {
			IdleStateEvent e = (IdleStateEvent)evt;
			switch (e.state()) {
			default:
				PingMsg pingMsg = new PingMsg();
				pingMsg.setType(String.valueOf(e.state()));
				pingMsg.setCport(Daemon.getInstance().getPort());
				ctx.writeAndFlush(pingMsg);
				break;
			}
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		logger.info("remote address: " + ctx.channel().remoteAddress() + " connect success!");
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, BaseMsg msg)
			throws Exception {
		MsgType msgType = msg.getT();
		switch (msgType) {
		case PING:
			logger.debug("receive ping from server[" + ctx.channel().remoteAddress() + "]");
			break;
			
		case CONNRES:
			ConnResMsg connResMsg = (ConnResMsg)msg;
			logger.debug("receive server msg: " + connResMsg.getStatus());
			break;
			
		case LOGINRES:
			LoginResMsg loginRes = (LoginResMsg)msg;
			processLoginRes(loginRes);
			break;
			
		case REGRES:		//中心注册响应
			logger.info(ctx.channel().remoteAddress().toString());
			RegisterResMsg regResMsg = (RegisterResMsg)msg;
			Map<String, Set<String>> sets = regResMsg.getServiceMap();
			ServerClientTransfer.parse(sets);
			logger.info("注册中心广播服务创建连接成功!");
			break;
		default:
			//进行游戏服务信息的解析，获取需要转发的数据
			String uId = msg.getUid();
			logger.info(JSONObject.toJSONString(msg));
			Channel channelU = AgentServerClientMap.get(uId);
			if(channelU != null) {
				if(msg instanceof MjCreateResMsg) {
					ConfigUtil.getClient().add(Constant.MEM_ROOM_PREFIX + msg.getRoomNo(), 
							ctx.channel().remoteAddress().toString().substring(1));
				}
				channelU.writeAndFlush(JSONObject.toJSONString(msg));
			}
			break;
		}
		
		ReferenceCountUtil.release(msg);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		//删除相应的连接
		String remote = ctx.channel().remoteAddress().toString();
		String remoteAddress = remote.substring(1);
		ServerClientTransfer.remove(remoteAddress);
		logger.error("remote address: " + ctx.channel().remoteAddress() + "," + cause.getMessage());
	}
	
	protected void processLoginRes(LoginResMsg loginRes) {
		Channel channel = AgentServerClientMap.get(loginRes.getCid());
		JSONObject obj = new JSONObject();
		obj.put(Constant.KEY_UID, loginRes.getUid());
		obj.put(Constant.KEY_TYPE, MsgType.LOGINRES.toString());
		if(loginRes.getStatus().equals("OK")) {
			AgentServerClientMap.put(loginRes.getUid(), (SocketChannel)channel);
			AgentServerClientMap.remove(loginRes.getCid());
			AgentServerClientMap.printCount();
			
			logger.info("用户[" + loginRes.getUsername() + "(" + loginRes.getUid() +")]登陆成功!");
			obj.put("status", "1");
			obj.put("message", "Login Success");
			channel.writeAndFlush(obj.toJSONString());
		} else {
			logger.info("用户[" + loginRes.getUsername() + "(" + loginRes.getUid() +")]登陆失败!");
			obj.put("status", "-1");
			obj.put("message", "Login Failed");
			channel.writeAndFlush(obj.toJSONString());
		}
	}
}
