'use strict';

const { WORKER_STATE } = require('./protocol');
const geometry = require('./geometry');
const smelter = require('./smelter');
const litematic = require('./litematic');

// Görev atama, worker durumları ve progress takibi. Ağdan bağımsız (transport
// katmanı index.js). Böylece test edilebilir kalır.
class Orchestrator {
  constructor(opts = {}) {
    this.workerCount = opts.workerCount || 4;
    // workerId -> { state, progress:{placedZ,placedCount}, segment, mcName,
    //               materialList, need:{item,count}|null }
    this.workers = new Map();
    this.job = null; // aktif iş: { corner1, corner2, structure, plan }
    this.emit = opts.emit || (() => {}); // (workerId, msgType, payload)
    this.emitMaster = opts.emitMaster || (() => {}); // (msgType, payload)
  }

  registerWorker(workerId, mcName) {
    let w = this.workers.get(workerId);
    if (!w) {
      w = {
        workerId,
        mcName,
        state: WORKER_STATE.IDLE,
        progress: { placedZ: 0, placedCount: 0 },
        segment: null,
        materialList: [],
        need: null,
      };
      this.workers.set(workerId, w);
    } else {
      w.mcName = mcName || w.mcName;
      // Yeniden bağlandı: iş varsa kaldığı yerden devam ettir.
      if (w.state === WORKER_STATE.DISCONNECTED && w.segment) {
        w.state = WORKER_STATE.BUILDING;
        this.emit(workerId, 'task:resume', { fromZ: w.progress.placedZ });
      }
    }
    this.pushStatus();
    return w;
  }

  markDisconnected(workerId) {
    const w = this.workers.get(workerId);
    if (!w) return;
    // Progress ve segment korunur; sadece işaretle. (Karar: bekle, unutma.)
    if (w.state !== WORKER_STATE.DONE) w.state = WORKER_STATE.DISCONNECTED;
    this.pushStatus();
  }

  // Master alan seçip start dediğinde.
  startJob({ corner1, corner2, structure = 'smelter' }) {
    if (structure !== 'smelter') {
      throw new Error('Desteklenmeyen yapı: ' + structure);
    }
    const plan = geometry.splitSelection(corner1, corner2, {
      workers: this.workerCount,
    });
    this.job = { corner1, corner2, structure, plan };

    // Segmentleri bağlı (idle/disconnected olmayan) worker'lara ata.
    const ready = [...this.workers.values()].filter(
      (w) => w.state !== WORKER_STATE.DISCONNECTED
    );
    if (ready.length < plan.segments.length) {
      // Eksik worker olsa da eldeki kadarına atarız; kalan segmentler beklemede.
      this.emitMaster('swarm:warn', {
        message: `Hazır worker (${ready.length}) < segment (${plan.segments.length}). Eksikler beklemede.`,
      });
    }

    const assignments = [];
    for (let i = 0; i < plan.segments.length; i++) {
      const seg = plan.segments[i];
      const worker = ready[i];
      const geom = smelter.generate(seg.length);
      const lito = litematic.build(geom, {
        name: `smelter_seg_${seg.segmentId}`,
        description: `Segment ${seg.segmentId} (len ${seg.length})`,
      });
      const payload = {
        segmentId: seg.segmentId,
        origin: seg.origin,
        size: geom.size,
        rotationY: seg.rotationY,
        litematic: lito.toString('base64'),
        materialList: geom.materialList,
        resumeFromZ: 0,
      };
      assignments.push({ worker: worker ? worker.workerId : null, payload, seg, geom });

      if (worker) {
        worker.state = WORKER_STATE.BUILDING;
        worker.segment = { ...seg, size: geom.size };
        worker.materialList = geom.materialList;
        worker.progress = { placedZ: 0, placedCount: 0 };
        worker.need = null;
        this.emit(worker.workerId, 'task:assign', payload);
      }
    }
    this.pushStatus();
    return { plan, assignments };
  }

  stopJob() {
    for (const w of this.workers.values()) {
      if (w.state === WORKER_STATE.BUILDING || w.state === WORKER_STATE.WAITING_MATERIAL) {
        this.emit(w.workerId, 'task:abort', {});
        w.state = WORKER_STATE.IDLE;
        w.segment = null;
        w.need = null;
      }
    }
    this.job = null;
    this.pushStatus();
  }

  onProgress(workerId, { placedZ, placedCount }) {
    const w = this.workers.get(workerId);
    if (!w) return;
    w.progress = { placedZ, placedCount };
    if (w.state === WORKER_STATE.WAITING_MATERIAL) w.state = WORKER_STATE.BUILDING;
    this.pushStatus();
  }

  onNeedMaterial(workerId, { item, count }) {
    const w = this.workers.get(workerId);
    if (!w) return;
    w.state = WORKER_STATE.WAITING_MATERIAL;
    w.need = { item, count };
    // Master'a bildir: "WORKER-x malzeme bekliyor".
    this.emitMaster('swarm:need_material', {
      workerId,
      mcName: w.mcName,
      item,
      count,
      progress: w.progress,
    });
    this.pushStatus();
  }

  onMaterialOk(workerId) {
    const w = this.workers.get(workerId);
    if (!w) return;
    w.need = null;
    w.state = WORKER_STATE.BUILDING;
    this.pushStatus();
  }

  onDone(workerId) {
    const w = this.workers.get(workerId);
    if (!w) return;
    w.state = WORKER_STATE.DONE;
    w.need = null;
    this.pushStatus();
    // Tüm worker'lar bitti mi?
    const active = [...this.workers.values()].filter((x) => x.segment);
    if (active.length && active.every((x) => x.state === WORKER_STATE.DONE)) {
      this.emitMaster('swarm:complete', {});
    }
  }

  onError(workerId, { message }) {
    const w = this.workers.get(workerId);
    if (!w) return;
    w.state = WORKER_STATE.ERROR;
    this.emitMaster('swarm:worker_error', { workerId, mcName: w.mcName, message });
    this.pushStatus();
  }

  statusSnapshot() {
    return {
      job: this.job
        ? { structure: this.job.structure, plan: this.job.plan }
        : null,
      workers: [...this.workers.values()].map((w) => ({
        workerId: w.workerId,
        mcName: w.mcName,
        state: w.state,
        progress: w.progress,
        need: w.need,
        segment: w.segment
          ? { segmentId: w.segment.segmentId, length: w.segment.length }
          : null,
      })),
    };
  }

  pushStatus() {
    this.emitMaster('swarm:status', this.statusSnapshot());
  }
}

module.exports = { Orchestrator };
