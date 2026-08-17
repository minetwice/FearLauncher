#include "fear_shader_cache.h"
#include "fear_shader_logger.h"
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <mutex>
#include <algorithm>
#include <dlfcn.h>
#include <iomanip>
#include <sstream>

static std::string g_cacheDir = "";
static std::mutex g_cacheMutex;

std::string calculate_sha256(const std::string& str) {
    unsigned int h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
    unsigned int h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

    static const unsigned int k[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    std::string msg = str;
    uint64_t bit_len = msg.size() * 8;
    msg += (char)0x80;
    while ((msg.size() + 8) % 64 != 0) {
        msg += (char)0x00;
    }
    for (int i = 7; i >= 0; --i) {
        msg += (char)((bit_len >> (i * 8)) & 0xff);
    }

    for (size_t chunk = 0; chunk < msg.size(); chunk += 64) {
        unsigned int w[64];
        for (int i = 0; i < 16; ++i) {
            w[i] = ((unsigned char)msg[chunk + i * 4] << 24) |
                   ((unsigned char)msg[chunk + i * 4 + 1] << 16) |
                   ((unsigned char)msg[chunk + i * 4 + 2] << 8) |
                   ((unsigned char)msg[chunk + i * 4 + 3]);
        }
        for (int i = 16; i < 64; ++i) {
            unsigned int s0 = ((w[i-15] >> 7) | (w[i-15] << 25)) ^
                              ((w[i-15] >> 18) | (w[i-15] << 14)) ^
                              (w[i-15] >> 3);
            unsigned int s1 = ((w[i-2] >> 17) | (w[i-2] << 15)) ^
                              ((w[i-2] >> 19) | (w[i-2] << 13)) ^
                              (w[i-2] >> 10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }

        unsigned int a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
        for (int i = 0; i < 64; ++i) {
            unsigned int s1 = ((e >> 6) | (e << 26)) ^
                              ((e >> 11) | (e << 21)) ^
                              ((e >> 25) | (e << 7));
            unsigned int ch = (e & f) ^ (~e & g);
            unsigned int temp1 = h + s1 + ch + k[i] + w[i];
            unsigned int s0 = ((a >> 2) | (a << 30)) ^
                              ((a >> 13) | (a << 19)) ^
                              ((a >> 22) | (a << 10));
            unsigned int maj = (a & b) ^ (a & c) ^ (b & c);
            unsigned int temp2 = s0 + maj;

            h = g;
            g = f;
            f = e;
            e = d + temp1;
            d = c;
            c = b;
            b = a;
            a = temp1 + temp2;
        }

        h0 += a; h1 += b; h2 += c; h3 += d;
        h4 += e; h5 += f; h6 += g; h7 += h;
    }

    std::stringstream ss;
    ss << std::hex << std::setfill('0');
    ss << std::setw(8) << h0 << std::setw(8) << h1 << std::setw(8) << h2 << std::setw(8) << h3
       << std::setw(8) << h4 << std::setw(8) << h5 << std::setw(8) << h6 << std::setw(8) << h7;
    return ss.str();
}

extern "C" {

void clearShaderCacheDir() {
    std::lock_guard<std::mutex> lock(g_cacheMutex);
    if (g_cacheDir.empty()) return;

    DIR* dir = opendir(g_cacheDir.c_str());
    if (!dir) return;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name != "." && name != "..") {
            std::string filepath = g_cacheDir + "/" + name;
            unlink(filepath.c_str());
        }
    }
    closedir(dir);
    LOG_INFO("[FearEngine] Shader cache cleared successfully.");
}

void initShaderCacheSystem(const std::string& cacheDir, int launcherVersion) {
    std::lock_guard<std::mutex> lock(g_cacheMutex);
    g_cacheDir = cacheDir;

    mkdir(g_cacheDir.c_str(), 0777);

    std::string verPath = g_cacheDir + "/version.txt";
    bool invalidate = true;
    FILE* f = fopen(verPath.c_str(), "r");
    if (f) {
        int ver = 0;
        if (fscanf(f, "%d", &ver) == 1) {
            if (ver == launcherVersion) {
                invalidate = false;
            }
        }
        fclose(f);
    }

    if (invalidate) {
        LOG_INFO("[FearEngine] Shader version mismatch or first boot. Invalidating old cache files.");
        DIR* dir = opendir(g_cacheDir.c_str());
        if (dir) {
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                std::string name = entry->d_name;
                if (name != "." && name != ".." && name != "version.txt") {
                    std::string filepath = g_cacheDir + "/" + name;
                    unlink(filepath.c_str());
                }
            }
            closedir(dir);
        }

        FILE* out = fopen(verPath.c_str(), "w");
        if (out) {
            fprintf(out, "%d", launcherVersion);
            fclose(out);
        }
    } else {
        LOG_INFO("[FearEngine] Shader cache initialized and version verified.");
    }
}

} // extern "C"

std::string getShaderSourceHash(const std::string& source) {
    return calculate_sha256(source);
}

bool loadProgramBinaryFromCache(GLuint program, const std::string& programHash, bool isGLES) {
    if (!isGLES) {
        return false;
    }

    std::lock_guard<std::mutex> lock(g_cacheMutex);
    if (g_cacheDir.empty() || programHash.empty()) return false;

    std::string binPath = g_cacheDir + "/" + programHash + "_gles.bin";
    std::string fmtPathCorrect = g_cacheDir + "/" + programHash + "_gles.fmt";

    FILE* f_bin = fopen(binPath.c_str(), "rb");
    if (!f_bin) return false;

    fseek(f_bin, 0, SEEK_END);
    long size = ftell(f_bin);
    fseek(f_bin, 0, SEEK_SET);

    if (size <= 0) {
        fclose(f_bin);
        return false;
    }

    GLenum format = 0;
    FILE* f_fmt = fopen(fmtPathCorrect.c_str(), "rb");
    if (!f_fmt) {
        fclose(f_bin);
        return false;
    }
    if (fread(&format, sizeof(GLenum), 1, f_fmt) != 1) {
        fclose(f_bin);
        fclose(f_fmt);
        return false;
    }
    fclose(f_fmt);

    void* buffer = malloc(size);
    if (!buffer) {
        fclose(f_bin);
        return false;
    }

    if (fread(buffer, 1, size, f_bin) != (size_t)size) {
        free(buffer);
        fclose(f_bin);
        return false;
    }
    fclose(f_bin);

    typedef void (*glProgramBinary_pfn)(GLuint, GLenum, const void*, GLsizei);
    static glProgramBinary_pfn real_glProgramBinary = nullptr;
    if (!real_glProgramBinary) {
        real_glProgramBinary = (glProgramBinary_pfn)dlsym(RTLD_NEXT, "glProgramBinary");
    }

    bool loaded = false;
    if (real_glProgramBinary) {
        real_glProgramBinary(program, format, buffer, size);

        GLint link_status = 0;
        typedef void (*glGetProgramiv_pfn)(GLuint, GLenum, GLint*);
        static glGetProgramiv_pfn real_glGetProgramiv = nullptr;
        if (!real_glGetProgramiv) {
            real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
        }
        if (real_glGetProgramiv) {
            real_glGetProgramiv(program, GL_LINK_STATUS, &link_status);
        }
        if (link_status) {
            loaded = true;
            LOG_INFO("[FearEngine] Loaded shader from cache (saved compilation time)");
        } else {
            LOG_WARNING("[FearEngine] Program binary found but failed to load successfully, corrupt cache or driver changed.");
            unlink(binPath.c_str());
            unlink(fmtPathCorrect.c_str());
        }
    }

    free(buffer);
    return loaded;
}

void saveProgramBinaryToCache(GLuint program, const std::string& programHash, bool isGLES) {
    if (!isGLES) return;

    std::lock_guard<std::mutex> lock(g_cacheMutex);
    if (g_cacheDir.empty() || programHash.empty()) return;

    GLint binary_len = 0;
    typedef void (*glGetProgramiv_pfn)(GLuint, GLenum, GLint*);
    static glGetProgramiv_pfn real_glGetProgramiv = nullptr;
    if (!real_glGetProgramiv) {
        real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
    }
    if (real_glGetProgramiv) {
        real_glGetProgramiv(program, GL_PROGRAM_BINARY_LENGTH, &binary_len);
    }

    if (binary_len <= 0) return;

    void* buffer = malloc(binary_len);
    if (!buffer) return;

    GLsizei written_len = 0;
    GLenum format = 0;
    typedef void (*glGetProgramBinary_pfn)(GLuint, GLsizei, GLsizei*, GLenum*, void*);
    static glGetProgramBinary_pfn real_glGetProgramBinary = nullptr;
    if (!real_glGetProgramBinary) {
        real_glGetProgramBinary = (glGetProgramBinary_pfn)dlsym(RTLD_NEXT, "glGetProgramBinary");
    }

    if (real_glGetProgramBinary) {
        real_glGetProgramBinary(program, binary_len, &written_len, &format, buffer);

        std::string binPath = g_cacheDir + "/" + programHash + "_gles.bin";
        std::string fmtPath = g_cacheDir + "/" + programHash + "_gles.fmt";

        FILE* f_bin = fopen(binPath.c_str(), "wb");
        if (f_bin) {
            fwrite(buffer, 1, written_len, f_bin);
            fclose(f_bin);

            FILE* f_fmt = fopen(fmtPath.c_str(), "wb");
            if (f_fmt) {
                fwrite(&format, sizeof(GLenum), 1, f_fmt);
                fclose(f_fmt);
                LOG_INFO("[FearEngine] Shader program compiled normally and saved to cache (Hash: %s)", programHash.c_str());
            }
        }
    }
    free(buffer);
}
