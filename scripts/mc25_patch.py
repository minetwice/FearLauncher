s = open('src/panfrost/vulkan/csf/panvk_vX_gpu_queue.c').read()

# ================= MC23: wait_seqno -> userspace polling + file markers =================

# ---- R6: file logger helper before the wait_seqno definition ----
whelp = """#include <stdarg.h>
#include <stdio.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <time.h>
#include <poll.h>

/* MC25: never block on the stdout pipe - if the Java log reader ever
 * stalls, a full pipe would hang every printing thread (part of the
 * original freeze). Check writability first, drop if full.
 * NOTE: aarch64 has no poll() syscall - ppoll() with a zero timeout
 * is the portable form. */
static int
panvk_mc25_fd_ok(int fd)
{
   struct pollfd p = { fd, POLLOUT, 0 };
   struct timespec z = { 0, 0 };
   return syscall(SYS_ppoll, &p, 1, &z, NULL, 8) == 1 && (p.revents & POLLOUT);
}

/* MC23: raw-syscall file logger - survives the freeze (the stdout pipe
 * reader dies ~1s after the hang starts). MC24: dump files live in BOTH
 * the private app dir AND the SHARED external dir next to latestlog.txt,
 * where the user can grab them without root. */
#define PANVK_WD_DUMP_PATH "/data/data/git.artdeell.mojo.debug/files/panvk_wd_dump.txt"
#define PANVK_WD_DUMP_EXT "/storage/emulated/0/Android/data/git.artdeell.mojo.debug/files/panvk_wd_dump.txt"

__attribute__((unused)) static void
panvk_mc23_flog(const char *fmt, ...)
{
   char buf[512];
   va_list va;
   va_start(va, fmt);
   int n = vsnprintf(buf, sizeof(buf), fmt, va);
   va_end(va);
   if (n <= 0)
      return;
   if (panvk_mc25_fd_ok(2))
      syscall(SYS_write, 2, buf, (size_t)n);
   long fd = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_PATH,
                     O_WRONLY | O_CREAT | O_APPEND, 0644);
   if (fd >= 0) {
      syscall(SYS_write, fd, buf, (size_t)n);
      syscall(SYS_close, fd);
   }
   fd = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_EXT,
                O_WRONLY | O_CREAT | O_APPEND, 0644);
   if (fd >= 0) {
      syscall(SYS_write, fd, buf, (size_t)n);
      syscall(SYS_close, fd);
   }
}

"""
wdef = "static VkResult\nkbase_subqueue_wait_seqno(struct panvk_gpu_queue *queue, uint32_t subqueue,\n                          uint64_t target_seqno, uint32_t rekick_mask,\n                          bool allow_ring_drain, uint64_t abs_timeout_ns)\n{\n"
if "MC23: raw-syscall file logger" not in s:
    assert s.count(wdef) == 1, "MC23 wdef anchor x%d" % s.count(wdef)
    s = s.replace(wdef, whelp + wdef, 1)
    print("MC23: flog helper inserted before wait_seqno")

# ---- R4: extra loop-state variables ----
dv = "   uint64_t deadline = MIN2(abs_timeout_ns, watchdog);"
if "mc23_slow_logged" not in s:
    assert s.count(dv) == 1, "deadline anchor x%d" % s.count(dv)
    s = s.replace(
        dv,
        dv + "\n   bool mc23_slow_logged = false;\n   int64_t mc23_last_poll = 0;",
        1,
    )
    print("MC23: loop-state vars added")

# ---- R2: re-kick ioctl markers ----
kstart = "      /* Re-kick periodically: a kick can race with an in-flight group"
kend = "         last_kick = now;\n      }\n"
knew = """      /* Re-kick periodically: a kick can race with an in-flight group
 * suspend and get dropped. MC23: kick ioctls are wrapped with file
 * markers while slow, so a hang inside the ioctl becomes visible. */
      if (now - last_kick > 500ll * 1000000ll) {
         bool mc23_kslow = (uint64_t)now - (uint64_t)start >
                           200ull * 1000000ull;
         u_foreach_bit(i, rekick_mask) {
            if (mc23_kslow)
               panvk_mc23_flog("MC23 KICK-ENTER subq=%u\\n", (unsigned)i);
            kbase_kmod_csf_queue_kick(
               dev->kmod.dev, queue->subqueues[i].kbase.ringbuf_dev);
            if (mc23_kslow)
               panvk_mc23_flog("MC23 KICK-EXIT subq=%u\\n", (unsigned)i);
         }
         last_kick = now;
      }
"""
if "MC23 KICK-ENTER" not in s:
    assert s.count(kstart) == 1 and s.count(kend) == 1
    i2 = s.index(kstart)
    j2 = s.index(kend, i2) + len(kend)
    s = s[:i2] + knew + s[j2:]
    print("MC23: kick markers applied")

# ---- R3: timeout file log ----
t3 = "      if ((uint64_t)now >= deadline) {\n         if (deadline < watchdog)\n            return VK_TIMEOUT;\n"
t3new = t3 + """

         panvk_mc23_flog("MC23 TIMEOUT subq=%u seq=%" PRIu64 " tgt=%" PRIu64
                         " ls=%" PRIu64 " marks=%" PRIx64 "/%" PRIx64
                         "/%" PRIx64 " prog=0x%x ins=%" PRIu64 " ext=%" PRIu64
                         " act=%u err=0x%x\\n",
                         subqueue, (uint64_t)cell->seqno, target_seqno,
                         *ls_copy, *mark_pre_call, *mark_post_call,
                         *mark_post_wait, *stream_progress, insert, extract,
                         active, cell->error);
"""
if "MC23 TIMEOUT" not in s:
    assert s.count(t3) == 1, "timeout anchor x%d" % s.count(t3)
    s = s.replace(t3, t3new, 1)
    print("MC23: timeout flog applied")

# ---- R1: replace the kernel CQS waits with userspace polling ----
r1s = "      /* Block on a CSF notification (consuming one event when available)"
r1e = "         prev_error_type = error_type;\n      }\n"
r1new = """      /* MC23: the kernel CQS waits (cqs64 + wait_event ioctls) were the
 * #1 hang suspect: the loop's own 10s watchdog can never fire while
 * blocked inside an ioctl that ignores its timeout. Observed freeze:
 * GPU idle, all submitted work done, render thread never returned
 * from this function, all process output dead. Sleep in userspace
 * instead and keep polling the mapped seqno cell - the MC21f
 * watchdog already proved plain userspace reads observe seqno
 * progress fine. Slow-wait state is appended to the on-disk dump so
 * the next freeze is diagnosable even when stdout is dead. */
      (void) seqno_addr;
      (void) prev_error_type;
      bool mc23_slow = (uint64_t)now - (uint64_t)start > 200ull * 1000000ull;
      if (mc23_slow && !mc23_slow_logged) {
         mc23_slow_logged = true;
         mc23_last_poll = now;
         panvk_mc23_flog("MC23 WAIT-SLOW subq=%u tgt=%" PRIu64 " cur=%" PRIu64
                         " ls=%" PRIu64 " ins=%" PRIu64 " ext=%" PRIu64
                         " act=%u err=0x%x\\n",
                         subqueue, target_seqno, (uint64_t)cell->seqno,
                         *ls_copy, insert, extract, active, cell->error);
      }
      if (mc23_slow && now - mc23_last_poll > 100ll * 1000000ll) {
         mc23_last_poll = now;
         panvk_mc23_flog("MC23 POLL subq=%u cur=%" PRIu64 "\\n",
                         subqueue, (uint64_t)cell->seqno);
      }
      usleep(1000);
"""
if "MC23: the kernel CQS waits" not in s:
    assert s.count(r1s) == 1 and s.count(r1e) == 1
    i1 = s.index(r1s)
    j1 = s.index(r1e, i1) + len(r1e)
    s = s[:i1] + r1new + s[j1:]
    print("MC23: kernel CQS waits replaced with userspace polling")

# ---- R5: WAIT-DONE marker after the loop ----
d5 = "   bool completed =\n      cell->seqno >= target_seqno ||"
d5new = """   if (mc23_slow_logged)
      panvk_mc23_flog("MC23 WAIT-DONE subq=%u tgt=%" PRIu64 " cur=%" PRIu64
                      " took_ms=%lld\\n",
                      subqueue, target_seqno, (uint64_t)cell->seqno,
                      (os_time_get_nano() - start) / 1000000ll);

   bool completed =
      cell->seqno >= target_seqno ||"""
if "MC23 WAIT-DONE" not in s:
    assert s.count(d5) == 1, "done anchor x%d" % s.count(d5)
    s = s.replace(d5, d5new, 1)
    print("MC23: WAIT-DONE marker applied")

# ================= MC21f: tracer + watchdog (file-persisted dump; MC25: meminfo + wchan + pipe guard) =================

# MC21c: tracer globals must be defined BEFORE first use
inc0 = '#include "util/os_time.h"'
assert inc0 in s, "os_time include anchor missing"
s = s.replace(inc0, inc0 + '\n\n/* MC21c: last-driver-entry tracer (watchdog prints it every 5s). */\n__attribute__((weak)) volatile uint64_t panvk_mc21c_op = 0;\n__attribute__((weak)) volatile int64_t panvk_mc21c_ms = 0;\n', 1)

anchor1 = "VkResult\npanvk_per_arch(create_gpu_queue)(struct panvk_device *dev,"
wd_fn = """#ifdef HAVE_PAN_KMOD_KBASE
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <signal.h>

/* MC21e: raw-syscall output helpers - at the freeze ALL threads appear to
 * stall at once (logcat capture, the Java watchdog and mesa_logi all die
 * in the same instant), which points to a spin loop holding a global lock
 * such as malloc. Everything the watchdog prints therefore goes through
 * raw write(2), which cannot block on malloc. */
#define PANVK_WD_DUMP_PATH2 "/data/data/git.artdeell.mojo.debug/files/panvk_wd_dump.txt"
#define PANVK_WD_DUMP_EXT2 "/storage/emulated/0/Android/data/git.artdeell.mojo.debug/files/panvk_wd_dump.txt"

/* MC21f: everything is ALSO appended to a file - the stdout pipe reader
 * dies ~1s after the freeze, so anything printed later would be lost. */
static void
panvk_wd_raw_write(const char *buf, int n)
{
   if (panvk_mc25_fd_ok(2))
      syscall(SYS_write, 2, buf, (size_t)n);
   if (panvk_mc25_fd_ok(1))
      syscall(SYS_write, 1, buf, (size_t)n);
   long fd = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_PATH2,
                     O_WRONLY | O_CREAT | O_APPEND, 0644);
   if (fd >= 0) {
      syscall(SYS_write, fd, buf, (size_t)n);
      syscall(SYS_close, fd);
   }
   fd = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_EXT2,
                O_WRONLY | O_CREAT | O_APPEND, 0644);
   if (fd >= 0) {
      syscall(SYS_write, fd, buf, (size_t)n);
      syscall(SYS_close, fd);
   }
}

static int
panvk_wd_read_file(const char *path, char *buf, size_t bufsz)
{
   long fd = syscall(SYS_openat, AT_FDCWD, path, O_RDONLY, 0);
   if (fd < 0)
      return -1;
   long n = syscall(SYS_read, fd, buf, bufsz - 1);
   syscall(SYS_close, fd);
   if (n <= 0)
      return -1;
   buf[n] = 0;
   return (int)n;
}

#define PANVK_WD_MAX_TIDS 384
struct panvk_wd_tid {
   int tid;
   unsigned long long cpu;
};
static struct panvk_wd_tid panvk_wd_prev[PANVK_WD_MAX_TIDS];

/* MC21e: dump every thread of the process: state, CPU consumed in the
 * last cycle, and the syscall it is blocked in. The stuck render
 * thread shows up as state=R with cpu ~= full cycle (userspace spin) or as
 * state=D/S with a syscall number. MC25: also the kernel wait channel
 * (wchan) - the definitive answer for D-state threads. */
static void
panvk_wd_dump_threads(const char *reason)
{
   char buf[1024];
   int n = snprintf(buf, sizeof(buf), "kbase: wd2 %s\\n", reason);
   if (n > 0)
      panvk_wd_raw_write(buf, n);

   struct wd_dirent_min { unsigned long long d_ino; long long d_off; unsigned short d_reclen; unsigned char d_type; char d_name[16]; };
   long dfd = syscall(SYS_openat, AT_FDCWD, "/proc/self/task", O_RDONLY | O_DIRECTORY, 0);
   if (dfd < 0)
      return;
   char dbuf[2048];
   for (;;) {
      long r = syscall(SYS_getdents64, dfd, dbuf, sizeof(dbuf));
      if (r <= 0)
         break;
      for (long off = 0; off < r; ) {
         struct wd_dirent_min *d = (struct wd_dirent_min *)(dbuf + off);
         off += d->d_reclen;
         int tid = atoi(d->d_name);
         if (tid <= 0)
            continue;
         char path[96], sbuf[1200];
         snprintf(path, sizeof(path), "/proc/self/task/%d/stat", tid);
         if (panvk_wd_read_file(path, sbuf, sizeof(sbuf)) <= 0)
            continue;
         char *close_paren = strrchr(sbuf, ')');
         if (!close_paren)
            continue;
         char comm[24];
         int ci = 0;
         char *cs = strchr(sbuf, '(');
         if (cs && cs < close_paren) {
            for (char *p = cs + 1; p < close_paren && ci < 22; p++)
               comm[ci++] = *p;
         }
         comm[ci] = 0;
         char state = close_paren[2];
         /* skip state + fields 4..13 (ppid .. cmajflt), then utime stime */
         char *q = close_paren + 3;
         while (*q == ' ')
            q++;
         for (int k = 0; k < 10; k++) {
           while (*q && *q != ' ')
              q++;
           while (*q == ' ')
              q++;
         }
         unsigned long long ut = strtoull(q, &q, 10);
         while (*q == ' ')
            q++;
         unsigned long long st = strtoull(q, &q, 10);
         unsigned long long cpu = ut + st;

         struct panvk_wd_tid *slot = NULL;
         for (int i = 0; i < PANVK_WD_MAX_TIDS; i++) {
            if (panvk_wd_prev[i].tid == tid) {
               slot = &panvk_wd_prev[i];
               break;
            }
            if (!slot && panvk_wd_prev[i].tid == 0)
               slot = &panvk_wd_prev[i];
         }
         unsigned long long dc = 0;
         if (slot) {
            dc = (slot->tid == 0) ? 0 : (cpu - slot->cpu);
            slot->tid = tid;
            slot->cpu = cpu;
         }

         char sysc[48];
         sysc[0] = 0;
         if (state == 'S' || state == 'D' || state == 't') {
            snprintf(path, sizeof(path), "/proc/self/task/%d/syscall", tid);
            if (panvk_wd_read_file(path, sbuf, sizeof(sbuf)) > 0) {
               int si = 0;
               for (char *p = sbuf; *p && *p != 10 && si < 40; p++)
                  sysc[si++] = *p;
               sysc[si] = 0;
            }
         }
         char wchan[64];
         wchan[0] = 0;
         if (state == 'S' || state == 'D' || state == 't') {
            snprintf(path, sizeof(path), "/proc/self/task/%d/wchan", tid);
            if (panvk_wd_read_file(path, wchan, sizeof(wchan)) > 0) {
               char *nl = strchr(wchan, 10);
               if (nl)
                  *nl = 0;
            } else {
               wchan[0] = 0;
            }
         }

         n = snprintf(buf, sizeof(buf),
                      "kbase: wd2 tid=%d comm=%s state=%c cpu=%llu.%02llus/3s%s%s%s%s\\n",
                      tid, comm, state, dc / 100, dc % 100,
                      sysc[0] ? " syscall=" : "", sysc,
                      wchan[0] ? " wchan=" : "", wchan);
         if (n > 0)
            panvk_wd_raw_write(buf, n);
      }
   }
   syscall(SYS_close, dfd);
}

/* Watchdog: dumps per-subqueue GPU state + last driver entry every 3s.
 * MC21e: output via raw write(2) + files that survive the freeze.
 * MC21f: dump every thread after ONE quiet cycle.
 * MC24: 3s cycle (was 5s) so the dump lands before the ANR kill.
 * MC25: also logs MemAvailable every cycle. */
static void *
panvk_kbase_watchdog_thread(void *data)
{
   struct panvk_gpu_queue *queue = data;
   uint64_t last_activity = 0;
   int stall = 0;
   char buf[512];
   pthread_setname_np(pthread_self(), "panvk-wd");
   signal(SIGPIPE, SIG_IGN);
   /* MC24: self-test - report to stdout whether BOTH dump files are
    * creatable, so a bad path shows up in latestlog instead of silently
    * losing the freeze dump. Negative fd = -errno. */
   {
      char tb[256];
      long fdp = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_PATH2, O_WRONLY | O_CREAT | O_APPEND, 0644);
      long fde = syscall(SYS_openat, AT_FDCWD, PANVK_WD_DUMP_EXT2, O_WRONLY | O_CREAT | O_APPEND, 0644);
      int tn = snprintf(tb, sizeof(tb), "kbase: wd dump self-test private=%ld ext=%ld (negative = -errno)\\n", fdp, fde);
      syscall(SYS_write, 2, tb, (size_t)tn);
      syscall(SYS_write, 1, tb, (size_t)tn);
      if (fdp >= 0) { syscall(SYS_write, fdp, tb, (size_t)tn); syscall(SYS_close, fdp); }
      if (fde >= 0) { syscall(SYS_write, fde, tb, (size_t)tn); syscall(SYS_close, fde); }
   }
   for (;;) {
      sleep(3);
      uint64_t activity = panvk_mc21c_ms;
      for (uint32_t i = 0; i < PANVK_SUBQUEUE_COUNT; i++) {
         struct panvk_subqueue *subq = &queue->subqueues[i];
         if (!subq->kbase.user_io)
            continue;
         const uint8_t *input_page = (const uint8_t *)subq->kbase.user_io + 4096;
         const uint8_t *output_page = (const uint8_t *)subq->kbase.user_io + 8192;
         uint64_t insert = *(volatile uint64_t *)(input_page + CS_USER_IO_INPUT_CS_INSERT);
         uint64_t extract = *(volatile uint64_t *)(output_page + CS_USER_IO_OUTPUT_CS_EXTRACT);
         uint32_t active = *(volatile uint32_t *)(output_page + CS_USER_IO_OUTPUT_CS_ACTIVE);
         volatile struct panvk_cs_sync64 *cell = kbase_subqueue_seqno_cell(queue, i);
         int n = snprintf(buf, sizeof(buf),
                          "kbase: wd subq=%u ins=%" PRIu64 " ext=%" PRIu64 " act=%u seq=%" PRIu64 " err=0x%x\\n",
                          i, insert, extract, active, cell->seqno, cell->error);
         if (n > 0)
            panvk_wd_raw_write(buf, n);
         activity += insert + extract + cell->seqno + active;
      }
      char mi[256];
      if (panvk_wd_read_file("/proc/meminfo", mi, sizeof(mi)) > 0) {
         char *mp = strstr(mi, "MemAvailable:");
         unsigned long long avail = 0;
         if (mp)
            avail = strtoull(mp + 13, NULL, 10);
         int mn = snprintf(buf, sizeof(buf),
                           "kbase: wd mem avail=%llu kB\\n", avail);
         if (mn > 0)
            panvk_wd_raw_write(buf, mn);
      }
      const char *opname = panvk_mc21c_op == 1 ? "gpu_queue_submit" :
                           panvk_mc21c_op == 2 ? "subqueue_wait_seqno" :
                           panvk_mc21c_op == 3 ? "compile_shaders" : "none";
      int64_t age_ms = (int64_t)(os_time_get_nano() / 1000000ll) - panvk_mc21c_ms;
      int n = snprintf(buf, sizeof(buf), "kbase: wd last_entry=%s age_ms=%" PRId64 "\\n",
                       opname, age_ms);
      if (n > 0)
         panvk_wd_raw_write(buf, n);
      if (activity != last_activity) {
         last_activity = activity;
         stall = 0;
      } else {
         stall++;
         if (stall >= 1)
            panvk_wd_dump_threads("STALL: no GPU submits, no seqno progress, no driver entry since last cycle");
      }
   }
   return NULL;
}
#endif

"""
assert anchor1 in s, "anchor1 missing"
s = s.replace(anchor1, wd_fn + anchor1, 1)

a = "panvk_per_arch(gpu_queue_submit)(struct vk_queue *vk_queue, struct vk_queue_submit *vk_submit)\n{\n   PAN_TRACE_FUNC(PAN_TRACE_VK_CSF);"
assert a in s, "submit anchor missing"
s = s.replace(a, a + "\n   panvk_mc21c_op = 1; panvk_mc21c_ms = (int64_t)(os_time_get_nano() / 1000000ll);", 1)

a = "kbase_subqueue_wait_seqno(struct panvk_gpu_queue *queue, uint32_t subqueue,\n                          uint64_t target_seqno, uint32_t rekick_mask,\n                          bool allow_ring_drain, uint64_t abs_timeout_ns)\n{\n   struct panvk_device *dev = to_panvk_device(queue->vk.base.device);"
assert a in s, "wait anchor missing"
s = s.replace(a, a + "\n   panvk_mc21c_op = 2; panvk_mc21c_ms = (int64_t)(os_time_get_nano() / 1000000ll);", 1)

anchor2 = """   result = init_queue(queue);
   if (result != VK_SUCCESS)
      goto err_cleanup_created;

   queue->vk.driver_submit = panvk_per_arch(gpu_queue_submit);"""
repl2 = """   result = init_queue(queue);
   if (result != VK_SUCCESS)
      goto err_cleanup_created;

#ifdef HAVE_PAN_KMOD_KBASE
   if (uses_kbase) {
      pthread_t wd_thread;
      if (pthread_create(&wd_thread, NULL, panvk_kbase_watchdog_thread, queue) == 0)
         mesa_logi("kbase: GPU-state watchdog thread started (MC21f: file-persisted thread dump)");
   }
#endif

   queue->vk.driver_submit = panvk_per_arch(gpu_queue_submit);"""
assert anchor2 in s, "anchor2 missing"
s = s.replace(anchor2, repl2, 1)
open('src/panfrost/vulkan/csf/panvk_vX_gpu_queue.c', 'w').write(s)
print("gpu_queue.c patched (MC21f watchdog + tracer + MC23 wait polling + MC25 diagnostics)")

t = open('src/panfrost/vulkan/panvk_vX_shader.c').read()
inc = '#include "vk_pipeline.h"'
assert inc in t, "shader include anchor missing"
t = t.replace(inc, inc + '\n#include "util/os_time.h"\n\n/* MC21c: entry tracer defined in panvk_vX_gpu_queue.c */\nextern volatile uint64_t panvk_mc21c_op;\nextern volatile int64_t panvk_mc21c_ms;\n', 1)

cs = 'panvk_compile_shaders(struct vk_device *vk_dev, uint32_t shader_count,\n                      struct vk_shader_compile_info *infos,\n                      const struct vk_graphics_pipeline_state *state,\n                      const struct vk_features *enabled_features,\n                      const VkAllocationCallbacks *pAllocator,\n                      struct vk_shader **shaders_out)\n{\n   panvk_per_arch(compiler_lock)();'
assert cs in t, "compile_shaders anchor missing"
t = t.replace(cs, cs.replace('{\n   panvk_per_arch(compiler_lock)();', '{\n   panvk_mc21c_op = 3; panvk_mc21c_ms = (int64_t)(os_time_get_nano() / 1000000ll);\n   panvk_per_arch(compiler_lock)();'), 1)

per = '      result = panvk_compile_shader(dev, &infos[i], state, vs_varying_layout,\n                                    noperspective_varyings_ptr, pAllocator,\n                                    &shaders_out[i]);'
assert per in t, "per-shader anchor missing"
per_new = '      mesa_logi("kbase: compiling shader %d/%u stage=%u name=%s", i + 1, shader_count,\n                infos[i].stage, infos[i].nir->info.name[0] ? infos[i].nir->info.name : "?");\n      result = panvk_compile_shader(dev, &infos[i], state, vs_varying_layout,\n                                    noperspective_varyings_ptr, pAllocator,\n                                    &shaders_out[i]);\n      mesa_logi("kbase: compiled shader %d/%u result=%d", i + 1, shader_count, (int)result);'
t = t.replace(per, per_new, 1)
open('src/panfrost/vulkan/panvk_vX_shader.c', 'w').write(t)
print("shader.c patched (compile_shaders tracer)")
