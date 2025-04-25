package com.appdynamics.extensions.webspheremq.metricscollector;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A miserable hack to encapsulate the state that is shared between queue collectors
 */
public final class QueueCollectorSharedState {

    private final ConcurrentHashMap<String, String> queueNameToType = new ConcurrentHashMap<>();

    private final static QueueCollectorSharedState INSTANCE = new QueueCollectorSharedState();

    public static QueueCollectorSharedState getInstance() {
        return INSTANCE;
    }

    private QueueCollectorSharedState(){}

    public void putQueueType(String name, String value){
        queueNameToType.put(name, value);
    }

    // Nullable
    public String getType(String name){
        return queueNameToType.get(name);
    }

    // Only exists for testing and should not normally ever be called.
    void resetForTest(){
        queueNameToType.clear();
    }



}
