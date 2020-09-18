/*
 * Copyright 2018 Qunar, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qunar.tc.qmq.delay.startup;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.qmq.base.MessageHeader;
import qunar.tc.qmq.common.Disposable;
import qunar.tc.qmq.configuration.DynamicConfig;
import qunar.tc.qmq.configuration.DynamicConfigLoader;
import qunar.tc.qmq.delay.DefaultDelayLogFacade;
import qunar.tc.qmq.delay.DelayLogFacade;
import qunar.tc.qmq.delay.ScheduleIndex;
import qunar.tc.qmq.delay.base.ReceivedDelayMessage;
import qunar.tc.qmq.delay.base.ReceivedResult;
import qunar.tc.qmq.delay.config.DefaultStoreConfiguration;
//import qunar.tc.qmq.delay.receiver.Receiver;
import qunar.tc.qmq.delay.store.model.RawMessageExtend;
import qunar.tc.qmq.delay.wheel.WheelTickManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;

/**
 * @author xufeng.deng dennisdxf@gmail.com
 * @since 2018-07-27 17:05
 */
public class ServerWrapper implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerWrapper.class);

    private ExecutorService receiveMessageExecutorService;
    private DelayLogFacade facade;
    private WheelTickManager wheelTickManager;
    //private Receiver receiver;
    private DynamicConfig config;
    private DefaultStoreConfiguration storeConfig;

    public void start() {
        init();
        startServer();
    }


    private void init() {
        //配置
        this.config = DynamicConfigLoader.load("delay.properties");
        storeConfig = new DefaultStoreConfiguration(config);
        //延迟日志处理Facade
        this.facade = new DefaultDelayLogFacade(storeConfig, this::iterateCallback);
        //标记轮操作类
        this.wheelTickManager = new WheelTickManager(storeConfig, facade);
        //this.receiver = new Receiver(config, facade);
    }

    //接收处理延迟消息
    public void process(ReceivedDelayMessage message){
        try {
            //添加消息到messageLog
            ReceivedResult result = facade.appendMessageLog(message);
        } catch (Throwable t) {
            //error(message, t);
        }
    }


    //回放messageLog处理
    private boolean iterateCallback(final ScheduleIndex index) {
        long scheduleTime = index.getScheduleTime();
        long offset = index.getOffset();
        if (wheelTickManager.canAdd(scheduleTime, offset)) {
            //加入时间轮
            wheelTickManager.addWHeel(index);
            return true;
        }

        return false;
    }


    private void startServer() {
        wheelTickManager.start();
        //启动schedulelog轮询线程
        facade.start();
        facade.blockUntilReplayDone();
    }

    @Override
    public void destroy() {
        facade.shutdown();
        wheelTickManager.shutdown();
    }

    public static void main(String[] args) {
        try {
            System.setProperty("qmq.conf","E:/delay-message/conf");
            ServerWrapper sw = new ServerWrapper();
            sw.init();
            sw.start();
            MessageHeader header = new MessageHeader();
            header.setMessageId("200916.233001.192.168.2.105.20725.7");
            header.setSubject("order.changed");
            String msg = "{\"messageId\":\"200916.233001.192.168.2.105.20725.7\",\"subject\":\"order.changed\",\"tags\":[],\"storeAtFailed\":false,\"durable\":true,\"attrs\":{\"qmq_expireTime\":\"1600271101650\",\"orderId\":\"13131232131\",\"qmq_appCode\":\"qmq_test\",\"qmq_createTime\":\"1600270201650\",\"name\":\"delay009\",\"qmq_scheduleReceiveTime\":\"1600270206650\"}} ";
            byte[] bytes = msg.getBytes(CharsetUtil.UTF_8);
            ByteBuf body = Unpooled.wrappedBuffer(bytes);
            //String body = "test";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            RawMessageExtend raw = new RawMessageExtend(header, body,1024, sdf.parse("2020-09-18 23:42:03").getTime());
            ReceivedDelayMessage message = new ReceivedDelayMessage(raw,new Date().getTime());
            sw.process(message);
            Thread.sleep(300000);
            sw.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
