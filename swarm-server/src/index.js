'use strict';

const http = require('http');
const { Server } = require('socket.io');
const cfg = require('./config');
const { MSG } = require('./protocol');
const { Orchestrator } = require('./orchestrator');

const httpServer = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true, workers: orch.workers.size }));
    return;
  }
  res.writeHead(404);
  res.end();
});

const io = new Server(httpServer, {
  cors: { origin: '*' },
  maxHttpBufferSize: 1e8, // .litematic base64 büyük olabilir
});

// worker/master socket kayıtları
const masters = new Set();
const workerSockets = new Map(); // workerId -> socket

const orch = new Orchestrator({
  workerCount: cfg.WORKER_COUNT,
  emit: (workerId, type, payload) => {
    const s = workerSockets.get(workerId);
    if (s) s.emit(type, payload);
  },
  emitMaster: (type, payload) => {
    for (const s of masters) s.emit(type, payload);
  },
});

io.use((socket, next) => {
  const token = socket.handshake.auth && socket.handshake.auth.token;
  if (token !== cfg.AUTH_TOKEN) {
    return next(new Error('unauthorized'));
  }
  next();
});

io.on('connection', (socket) => {
  const role = socket.handshake.auth && socket.handshake.auth.role;
  console.log(`[conn] ${socket.id} role=${role}`);

  if (role === 'master') {
    masters.add(socket);
    socket.emit(MSG.SWARM_STATUS, orch.statusSnapshot());

    socket.on(MSG.SWARM_START, (data, ack) => {
      try {
        const result = orch.startJob(data);
        console.log(
          `[start] segments=${result.plan.segments.length} axis=${result.plan.lengthAxis} len=${result.plan.totalLength}`
        );
        if (ack) ack({ ok: true, plan: result.plan });
      } catch (e) {
        console.error('[start:error]', e.message);
        if (ack) ack({ ok: false, error: e.message });
        socket.emit('swarm:error', { message: e.message });
      }
    });

    socket.on(MSG.SWARM_STOP, () => {
      orch.stopJob();
    });

    socket.on('disconnect', () => {
      masters.delete(socket);
      console.log('[master:disconnect]', socket.id);
    });
    return;
  }

  if (role === 'worker') {
    let workerId = null;

    socket.on(MSG.WORKER_HELLO, (data) => {
      workerId = String(data.workerId);
      workerSockets.set(workerId, socket);
      orch.registerWorker(workerId, data.mcName);
      console.log(`[worker:hello] ${workerId} (${data.mcName})`);
    });

    socket.on(MSG.WORKER_PROGRESS, (data) => {
      if (workerId) orch.onProgress(workerId, data);
    });
    socket.on(MSG.WORKER_NEED_MATERIAL, (data) => {
      if (workerId) orch.onNeedMaterial(workerId, data);
    });
    socket.on(MSG.WORKER_MATERIAL_OK, () => {
      if (workerId) orch.onMaterialOk(workerId);
    });
    socket.on(MSG.WORKER_DONE, () => {
      if (workerId) orch.onDone(workerId);
    });
    socket.on(MSG.WORKER_ERROR, (data) => {
      if (workerId) orch.onError(workerId, data);
    });

    socket.on('disconnect', () => {
      if (workerId) {
        workerSockets.delete(workerId);
        orch.markDisconnected(workerId);
        console.log('[worker:disconnect]', workerId);
      }
    });
    return;
  }

  console.warn('[conn] bilinmeyen role, kapatılıyor:', role);
  socket.disconnect(true);
});

httpServer.listen(cfg.PORT, cfg.HOST, () => {
  console.log(`swarm-server dinliyor: ${cfg.HOST}:${cfg.PORT}`);
  console.log(`worker sayısı: ${cfg.WORKER_COUNT}, gap: ${cfg.SAFETY_GAP}`);
});

module.exports = { io, orch, httpServer };
