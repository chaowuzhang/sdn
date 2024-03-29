package net.floodlightcontroller.bandwidth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;



public class MonitorBandwidth implements IFloodlightModule, IMonitorBandwidthService {

    
    private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
    
    protected static IFloodlightProviderService floodlightProvider;
    //链路数据分析模块，已经由Floodlight实现了，我们只需要调用一下就可以，然后对结果稍做加工，便于我们自己使用
    protected static IStatisticsService statisticsService;
    
    private static IThreadPoolService threadPoolService;
    
    private static ScheduledFuture<?> portBandwidthCollector;
    
    private static IOFSwitchService switchService;
    //存放每条俩路的带宽使用情况 
    private static Map<NodePortTuple,SwitchPortBandwidth> bandwidth;
    //搜集数据的周期
    private static final int portBandwidthInterval = 4;
    
    /**
     * 获取带宽使用情况，计算带宽：
     * switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     */
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap () {
    	bandwidth = statisticsService.getBandwidthConsumption();
        Iterator<Entry<NodePortTuple, SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
        while(iter.hasNext()) {
        	Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
            NodePortTuple tuple  = entry.getKey();
            SwitchPortBandwidth switchPortBand = entry.getValue();
            System.out.print("节点id以及端口号：" + tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
            System.out.println("端口带宽：" + switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024));
            }
            return bandwidth;
        }
     

    //告诉FL，我们添加了一个模块，提供了IMonitorBandwidthService
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMonitorBandwidthService.class);
        return l;
    }
    //我们前面声明了几个需要使用的service,在这里说明一下实现类
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IMonitorBandwidthService.class, this);
        return m;
    }

    //告诉FL我们以来那些服务，以便于加载  依赖
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStatisticsService.class);
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    //初始化这些service,个人理解这个要早于startUp()方法的执行，验证很简单，在两个方法里打印当前时间就可以。
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        statisticsService = context.getServiceImpl(IStatisticsService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        startCollectBandwidth();
    }

    //自定义的开始收集数据的方法，使用了线程池，定周期的执行
    private synchronized void startCollectBandwidth(){
        portBandwidthCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new GetBandwidthThread(), portBandwidthInterval, portBandwidthInterval, TimeUnit.SECONDS);
        log.warn("Statistics collection thread(s) started");
    }

    //自定义的线程类，在上面的方法中实例化，并被调用
    /**
     * Single thread for collecting switch statistics and
     * containing the reply.
     */
    private class GetBandwidthThread extends Thread implements Runnable  {
        private Map<NodePortTuple,SwitchPortBandwidth> bandwidth;

        public Map<NodePortTuple, SwitchPortBandwidth> getBandwidth() {
            return bandwidth;
        }

//      public void setBandwidth(Map<NodePortTuple, SwitchPortBandwidth> bandwidth) {
//          this.bandwidth = bandwidth;
//      }

        @Override
        public void run() {
            System.out.println("GetBandwidthThread run()....");
            bandwidth =getBandwidthMap(); 
            System.out.println("bandwidth.size():"+bandwidth.size());
        }
    }

//    /**
//     * 获取带宽使用情况
//     * 需要简单的换算 
//        根据 switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
//        计算带宽
//     */
//    public Map<NodePortTuple,SwitchPortBandwidth> getBandwidthMap(){
//        bandwidth = statisticsService.getBandwidthConsumption();
////      
////      for(NodePortTuple tuple:bandwidth.keySet()){
////          System.out.println(tuple.getNodeId().toString()+","+tuple.getPortId().getPortNumber());
////          System.out.println();
////      }
//        Iterator<Entry<NodePortTuple,SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
//        while (iter.hasNext()) {
//            Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
//            NodePortTuple tuple  = entry.getKey();
//            SwitchPortBandwidth switchPortBand = entry.getValue();
//            System.out.print(tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
//            System.out.println(switchPortBand.getBitsPerSecondRx().getValue()/(8.0*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8.0*1024));
//
//        }
//
//
//        return bandwidth;
//    }

}