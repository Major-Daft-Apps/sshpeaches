#!/usr/bin/env python3
# Impairing TCP proxy for the SSHPeaches network tests (LiveNetworkResilienceTest): it listens on
# NETPROXY_LISTEN (default 127.0.0.1:56323) and forwards to a real SSH server at NETPROXY_UPSTREAM (default
# 127.0.0.1:56422). It is steered through netctl.json in NETPROXY_DIR (default: this script's directory), which
# netctl-watch.sh fills from the NETCTL lines the test logs; it re-reads the file every 100 ms:
#   delay_ms / jitter_ms : added one-way delay per chunk, each direction
#   rate_kBps : bandwidth of the link in KB/s per direction, shared by all connections (0: unlimited). The proxy
#     reads only as fast as it forwards, so the sender is held back like on a real slow link.
#   mode: pass | stall (stop forwarding, keep sockets) | refuse (close new conns)
#   reset: true closes every open connection once per new control message
#   once: the new_conn policy applies only to the first connection after the message
#   new_conn: pass | stall (accept, forward nothing) | stall_after:N (stall once the server sent N bytes)
import asyncio, json, os, random, time
DIR = os.environ.get('NETPROXY_DIR', os.path.dirname(os.path.abspath(__file__)))
CTL = os.path.join(DIR, 'netctl.json'); LOG = os.path.join(DIR, 'netproxy.log')
LISTEN_HOST, LISTEN_PORT = os.environ.get('NETPROXY_LISTEN', '127.0.0.1:56323').rsplit(':', 1)
UP_HOST, UP_PORT = os.environ.get('NETPROXY_UPSTREAM', '127.0.0.1:56422').rsplit(':', 1)
state = {'delay_ms': 0, 'jitter_ms': 0, 'mode': 'pass', 'new_conn': 'pass', 'gen': 0}
conns = set(); seen_epoch = 0; once_epoch = -1
# One token bucket per direction (True: server to client), in bytes.
buckets = {True: [0.0, time.monotonic()], False: [0.0, time.monotonic()]}
async def take_bandwidth(s2c, n):
    rate = state.get('rate_kBps', 0) * 1000
    if rate <= 0: return
    bucket = buckets[s2c]
    while True:
        now = time.monotonic()
        bucket[0] = min(rate * 0.25, bucket[0] + (now - bucket[1]) * rate); bucket[1] = now
        if bucket[0] >= n or bucket[0] >= rate * 0.25:
            bucket[0] -= n; return
        await asyncio.sleep(max(0.005, (n - bucket[0]) / rate))
# Every new control message (its gen differs from the last one read) starts a new epoch; resets and once-policies key
# on the epoch, since tests restart their gen numbering.
epoch = 0; last_file_gen = None
def log(msg):
    with open(LOG, 'a') as f: f.write(time.strftime('%H:%M:%S ') + msg + '\n')
async def control():
    global seen_epoch, epoch, last_file_gen
    while True:
        try:
            s = json.load(open(CTL))
            if s.get('gen') != last_file_gen:
                last_file_gen = s.get('gen'); epoch += 1
                state.update(s); log('ctl epoch=%d %s' % (epoch, json.dumps(state)))
            if state.get('reset') and epoch != seen_epoch:
                seen_epoch = epoch
                n = len(conns)
                for c in list(conns): c.abort()
                log('reset %d conns' % n)
        except Exception: pass
        await asyncio.sleep(0.1)
class Conn:
    def __init__(self, cr, cw, sr, sw, policy):
        self.cr, self.cw, self.sr, self.sw, self.policy = cr, cw, sr, sw, policy
        self.down = 0; self.dead = False
    def abort(self):
        self.dead = True
        for w in (self.cw, self.sw):
            try: w.transport.abort()
            except Exception: pass
    def stalled(self, server_to_client):
        if state.get('mode') == 'stall': return True
        p = self.policy
        if p == 'stall': return True
        if p.startswith('stall_after:') and server_to_client is not None and self.down >= int(p.split(':')[1]): return True
        return False
    async def pump(self, r, w, s2c):
        # Reading and writing run apart, so a delay adds latency without capping throughput: each chunk is written
        # delay_ms after it arrived, as on a real slow link, while later chunks keep arriving.
        # Bounded, so a slow link holds the sender back instead of the proxy buffering everything.
        queue = asyncio.Queue(maxsize=16)
        async def reader():
            try:
                while not self.dead:
                    data = await r.read(65536)
                    if not data: break
                    d = state.get('delay_ms', 0) + random.uniform(0, state.get('jitter_ms', 0))
                    await queue.put((time.monotonic() + d / 1000, data))
            except Exception: pass
            await queue.put(None)
        async def writer():
            try:
                while not self.dead:
                    item = await queue.get()
                    if item is None: break
                    due, data = item
                    if s2c and self.policy.startswith('stall_after:'):
                        limit = int(self.policy.split(':')[1]); room = max(0, limit - self.down)
                        if len(data) > room:
                            head, data = data[:room], data[room:]
                            if head:
                                self.down += len(head); w.write(head); await w.drain()
                            log('stall_after reached (%d)' % self.down)
                    while self.stalled(s2c) and not self.dead: await asyncio.sleep(0.05)
                    wait = due - time.monotonic()
                    if wait > 0: await asyncio.sleep(wait)
                    await take_bandwidth(s2c, len(data))
                    if s2c: self.down += len(data)
                    w.write(data); await w.drain()
            except Exception: pass
        try:
            await asyncio.gather(reader(), writer())
        finally:
            self.abort()

async def handle(cr, cw):
    if state.get('mode') == 'refuse':
        cw.transport.abort(); log('refused'); return
    try: sr, sw = await asyncio.open_connection(UP_HOST, int(UP_PORT))
    except Exception as e: log('upstream failed %s' % e); cw.close(); return
    global once_epoch
    policy = state.get('new_conn', 'pass')
    if state.get('once'):
        # once: the policy hits only the first connection after each new control message; later ones pass.
        if once_epoch == epoch: policy = 'pass'
        else: once_epoch = epoch
    c = Conn(cr, cw, sr, sw, policy); conns.add(c); log('conn policy=%s' % c.policy)
    await asyncio.gather(c.pump(cr, sw, False), c.pump(sr, cw, True))
    conns.discard(c); log('closed down=%d' % c.down)
async def main():
    json.dump({'delay_ms': 0, 'jitter_ms': 0, 'mode': 'pass', 'new_conn': 'pass', 'gen': 0}, open(CTL, 'w'))
    asyncio.create_task(control())
    srv = await asyncio.start_server(handle, LISTEN_HOST, int(LISTEN_PORT))
    log('listening'); await srv.serve_forever()
asyncio.run(main())
