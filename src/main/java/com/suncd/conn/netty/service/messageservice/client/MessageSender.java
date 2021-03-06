
package com.suncd.conn.netty.service.messageservice.client;

import com.suncd.conn.netty.dao.ConnSendMainDao;
import com.suncd.conn.netty.dao.ConnSendMainHisDao;
import com.suncd.conn.netty.dao.ConnSendMsgDao;
import com.suncd.conn.netty.dao.ConnTotalNumDao;
import com.suncd.conn.netty.entity.ConnSendMain;
import com.suncd.conn.netty.entity.ConnSendMainHis;
import com.suncd.conn.netty.entity.ConnSendMsg;
import com.suncd.conn.netty.utils.MsgCreator;
import com.suncd.conn.netty.system.constants.Constant;
import com.suncd.conn.netty.utils.SpringUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MessageSender implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSender.class);
    private static final Logger LOGGER_WARN = LoggerFactory.getLogger("warnAndErrorLogger");
    private ConnSendMainDao connSendMainDao = SpringUtil.getBean(ConnSendMainDao.class);
    private ConnSendMsgDao connSendMsgDao = SpringUtil.getBean(ConnSendMsgDao.class);
    private ConnSendMainHisDao connSendMainHisDao = SpringUtil.getBean(ConnSendMainHisDao.class);
    private ConnTotalNumDao connTotalNumDao = SpringUtil.getBean(ConnTotalNumDao.class);
    private Environment environment = SpringUtil.getBean(Environment.class);

    private Channel channel;

    public MessageSender() {

    }

    public MessageSender(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void run() {
        boolean running = true;
        // 间隔频率,单位:毫秒
        int fps = Integer.parseInt(environment.getProperty("netty.sendWaitTime"));
        while (running) {
            if (!this.channel.isActive()) {
                running = false;
                continue;
            }
            try {
                // 计数器
                int records = 0;
                // 1.获取待发电文
                List<ConnSendMain> connSendMains = connSendMainDao.selectByReceiver(Constant.SOCKET_SZ);
                for (ConnSendMain connSendMain : connSendMains) {
                    records++;
                    ConnSendMsg connSendMsg = connSendMsgDao.selectByPrimaryKey(connSendMain.getMsgId());

                    if (null == connSendMsg || null == connSendMsg.getMsgTxt() || connSendMsg.getMsgTxt().length() < 5) {
                        LOGGER_WARN.warn("消息内容为空或长度不够,主键id:{}", connSendMain.getMsgId());

                        // 删除发送总表
                        connSendMainDao.deleteByPrimaryKey(connSendMain.getId());

                        // 插入发送历史表
                        ConnSendMainHis connSendMainHis = new ConnSendMainHis();
                        connSendMainHis.setId(UUID.randomUUID().toString());
                        connSendMainHis.setCreateTime(connSendMain.getCreateTime());
                        connSendMainHis.setMsgId(connSendMain.getMsgId());
                        connSendMainHis.setSendFlag("0");
                        connSendMainHis.setSendResult("失败:消息长度不够或内容为空");
                        connSendMainHis.setTelId(connSendMain.getTelId());
                        connSendMainHis.setSender(connSendMain.getSender());
                        connSendMainHis.setSenderName(connSendMain.getSenderName());
                        connSendMainHis.setReceiver(connSendMain.getReceiver());
                        connSendMainHis.setReceiverName(connSendMain.getReceiverName());
                        connSendMainHis.setSendTime(new Date());
                        connSendMainHisDao.insertSelective(connSendMainHis);

                        // 更新统计表
                        connTotalNumDao.updateTotalNum("SE");

                    } else {
                        int pushTime = (int) (new Date().getTime() / 1000); // 时间标记
                        short seqNo = Constant.getSeqNo(); // 循环发送序号
                        if (connSendMain.getPushLongTime() == null || connSendMain.getPushSeqNo() == null || connSendMain.getPushLongTime() == 0 || connSendMain.getPushSeqNo() == 0) {
                            // 2.更新发送总表数据
                            connSendMain.setPushLongTime(pushTime);
                            connSendMain.setPushSeqNo((int) seqNo);
                            connSendMainDao.updateByPrimaryKeySelective(connSendMain);

                            // 3.发送消息
                            sendMsg(connSendMsg.getMsgTxt(), pushTime, seqNo);

                            // 每条记录间隔5秒发送
                            if (records < connSendMains.size()) {
                                try {
                                    Thread.sleep(fps);
                                } catch (InterruptedException e) {
                                    LOGGER_WARN.error(e.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER_WARN.error(e.getMessage(), e);
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER_WARN.error(e.getMessage());
            }
        }
        LOGGER_WARN.warn("【客户端】客户端连接通道:{}异常,已关闭消息发送线程!", this.channel.hashCode());
    }

    private void sendMsg(String msg, int pushTime, short seqNo) {
        byte[] data = MsgCreator.createAppData(msg, pushTime, seqNo);
        ByteBuf bf = this.channel.alloc().buffer(data.length);
        bf.writeBytes(data);
        this.channel.writeAndFlush(bf);

        // 记录预发送日志 - 2020-01-07 09:55
        LOGGER.info("消息发送完成,等待回执,消息标识:{},消息内容: {}", pushTime, msg);

    }
}
