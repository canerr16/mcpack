'use strict';

// Master <-> Server <-> Worker WebSocket mesaj tipleri.
// Tek kaynak: hem sunucu hem (dokümantasyon olarak) mod tarafı buna uyar.

const MSG = {
  // Master -> Server
  MASTER_HELLO: 'master:hello',
  SWARM_START: 'swarm:start', // { corner1:{x,y,z}, corner2:{x,y,z}, structure:'smelter' }
  SWARM_STOP: 'swarm:stop',

  // Worker -> Server
  WORKER_HELLO: 'worker:hello', // { workerId, mcName }
  WORKER_PROGRESS: 'worker:progress', // { placedZ, placedCount }
  WORKER_NEED_MATERIAL: 'worker:need_material', // { item, count }
  WORKER_MATERIAL_OK: 'worker:material_ok', // worker malzemeyi topladı, devam edecek
  WORKER_DONE: 'worker:done',
  WORKER_ERROR: 'worker:error', // { message }

  // Server -> Worker
  TASK_ASSIGN: 'task:assign',
  // {
  //   segmentId, origin:{x,y,z}, size:{x,y,z}, rotationY,
  //   litematic: <base64 gzip>, materialList:[{item,count}], resumeFromZ
  // }
  TASK_RESUME: 'task:resume', // { fromZ }
  TASK_ABORT: 'task:abort',

  // Server -> Master
  SWARM_STATUS: 'swarm:status', // { workers:[{workerId,state,progress,segment}] }
};

const WORKER_STATE = {
  IDLE: 'idle',
  BUILDING: 'building',
  WAITING_MATERIAL: 'waiting_material',
  DONE: 'done',
  ERROR: 'error',
  DISCONNECTED: 'disconnected',
};

module.exports = { MSG, WORKER_STATE };
