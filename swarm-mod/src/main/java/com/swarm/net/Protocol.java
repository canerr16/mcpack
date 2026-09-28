package com.swarm.net;

/** WebSocket mesaj tipleri — swarm-server/src/protocol.js ile birebir aynı. */
public final class Protocol {
    private Protocol() {}

    // Master -> Server
    public static final String MASTER_HELLO = "master:hello";
    public static final String SWARM_START = "swarm:start";
    public static final String SWARM_STOP = "swarm:stop";

    // Worker -> Server
    public static final String WORKER_HELLO = "worker:hello";
    public static final String WORKER_PROGRESS = "worker:progress";
    public static final String WORKER_NEED_MATERIAL = "worker:need_material";
    public static final String WORKER_MATERIAL_OK = "worker:material_ok";
    public static final String WORKER_DONE = "worker:done";
    public static final String WORKER_ERROR = "worker:error";

    // Server -> Worker
    public static final String TASK_ASSIGN = "task:assign";
    public static final String TASK_RESUME = "task:resume";
    public static final String TASK_ABORT = "task:abort";

    // Server -> Master
    public static final String SWARM_STATUS = "swarm:status";
    public static final String SWARM_NEED_MATERIAL = "swarm:need_material";
    public static final String SWARM_COMPLETE = "swarm:complete";
    public static final String SWARM_WORKER_ERROR = "swarm:worker_error";
    public static final String SWARM_WARN = "swarm:warn";
    public static final String SWARM_ERROR = "swarm:error";
}
