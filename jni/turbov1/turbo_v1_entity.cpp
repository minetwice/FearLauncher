#include "turbo_v1_entity.h"
#include "turbo_v1_perf.h"
#include <dlfcn.h>
#include <cstring>
#include <cmath>

namespace turbo_v1 {

EntityManager& EntityManager::instance() {
    static EntityManager s_instance;
    return s_instance;
}

void EntityManager::init() {
    m_entities.reserve(1024);
    m_instance_buffer.resize(1024 * 16);
    m_instance_buffer_capacity = 1024;
    m_initialized.store(true);
    LOGI("Indus2.0: Entity Batch Renderer initialized (capacity=1024)");
}

void EntityManager::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_entities.clear();
    m_entity_index.clear();
    m_instance_buffer.clear();
    m_initialized.store(false);
    LOGI("Indus2.0: Entity Batch Renderer shut down");
}

void EntityManager::begin_frame(const Frustum& frustum) {
    m_current_frustum = frustum;
}

void EntityManager::end_frame() {
    m_drawn.store(0);
    m_batched.store(0);
    m_culled.store(0);
}

bool EntityManager::is_in_frustum(const float* bbox_min, const float* bbox_max) const {
    for (int i = 0; i < 6; i++) {
        const float* plane = m_current_frustum.planes[i];
        float px = (plane[0] >= 0) ? bbox_max[0] : bbox_min[0];
        float py = (plane[1] >= 0) ? bbox_max[1] : bbox_min[1];
        float pz = (plane[2] >= 0) ? bbox_max[2] : bbox_min[2];
        float distance = plane[0]*px + plane[1]*py + plane[2]*pz + plane[3];
        if (distance < 0) return false;
    }
    return true;
}

uint32_t EntityManager::register_entity(uint32_t entity_id, const float* model_matrix,
                                         const float* bbox_min, const float* bbox_max,
                                         uint32_t texture_id, uint32_t vertex_offset,
                                         uint32_t index_count) {
    std::lock_guard<std::mutex> lock(m_mutex);
    auto it = m_entity_index.find(entity_id);
    if (it != m_entity_index.end()) {
        EntityBatchEntry& entry = m_entities[it->second];
        memcpy(entry.model_matrix, model_matrix, sizeof(float) * 16);
        memcpy(entry.bbox_min, bbox_min, sizeof(float) * 3);
        memcpy(entry.bbox_max, bbox_max, sizeof(float) * 3);
        entry.texture_id = texture_id;
        entry.vertex_offset = vertex_offset;
        entry.index_count = index_count;
        entry.visible = true;
        return entity_id;
    }
    EntityBatchEntry entry;
    entry.entity_id = entity_id;
    memcpy(entry.model_matrix, model_matrix, sizeof(float) * 16);
    memcpy(entry.bbox_min, bbox_min, sizeof(float) * 3);
    memcpy(entry.bbox_max, bbox_max, sizeof(float) * 3);
    entry.texture_id = texture_id;
    entry.vertex_offset = vertex_offset;
    entry.index_count = index_count;
    entry.visible = true;
    entry.frustum_culled = false;
    size_t idx = m_entities.size();
    m_entities.push_back(entry);
    m_entity_index[entity_id] = idx;
    if (idx >= m_instance_buffer_capacity) {
        m_instance_buffer_capacity *= 2;
        m_instance_buffer.resize(m_instance_buffer_capacity * 16);
    }
    return entity_id;
}

void EntityManager::unregister_entity(uint32_t entity_id) {
    std::lock_guard<std::mutex> lock(m_mutex);
    auto it = m_entity_index.find(entity_id);
    if (it == m_entity_index.end()) return;
    size_t idx = it->second;
    size_t last = m_entities.size() - 1;
    if (idx != last) {
        m_entities[idx] = m_entities[last];
        m_entity_index[m_entities[idx].entity_id] = idx;
    }
    m_entities.pop_back();
    m_entity_index.erase(entity_id);
}

void EntityManager::update_entity_transform(uint32_t entity_id, const float* model_matrix) {
    std::lock_guard<std::mutex> lock(m_mutex);
    auto it = m_entity_index.find(entity_id);
    if (it == m_entity_index.end()) return;
    memcpy(m_entities[it->second].model_matrix, model_matrix, sizeof(float) * 16);
}

void EntityManager::submit_batched_draws() {
    if (!m_initialized.load()) return;
    typedef void (*PFN_glDrawElementsInstanced)(GLenum, GLsizei, GLenum, const void*, GLsizei);
    typedef void (*PFN_glDrawArraysInstanced)(GLenum, GLint, GLsizei, GLsizei);
    typedef void (*PFN_glDrawElements)(GLenum, GLsizei, GLenum, const void*);
    typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    typedef GLenum (*PFN_glGetError)(void);
    typedef void (*PFN_glBindTexture)(GLenum, GLuint);
    typedef void (*PFN_glActiveTexture)(GLenum);
    static PFN_glDrawElementsInstanced real_draw_inst = nullptr;
    static PFN_glDrawElements real_draw = nullptr;
    static PFN_glBindBuffer real_bind = nullptr;
    static PFN_glBufferSubData real_sub = nullptr;
    static PFN_glGetError real_err = nullptr;
    static PFN_glBindTexture real_bind_tex = nullptr;
    static PFN_glActiveTexture real_active = nullptr;
    if (!real_draw_inst) real_draw_inst = (PFN_glDrawElementsInstanced)dlsym(RTLD_DEFAULT, "glDrawElementsInstanced");
    if (!real_draw)      real_draw       = (PFN_glDrawElements)dlsym(RTLD_DEFAULT, "glDrawElements");
    if (!real_bind)      real_bind       = (PFN_glBindBuffer)dlsym(RTLD_DEFAULT, "glBindBuffer");
    if (!real_sub)       real_sub        = (PFN_glBufferSubData)dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!real_err)       real_err        = (PFN_glGetError)dlsym(RTLD_DEFAULT, "glGetError");
    if (!real_bind_tex)  real_bind_tex   = (PFN_glBindTexture)dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!real_active)    real_active     = (PFN_glActiveTexture)dlsym(RTLD_DEFAULT, "glActiveTexture");
    auto flush_err = [&]() {
        if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }
    };
    std::lock_guard<std::mutex> lock(m_mutex);
    int drawn = 0, batched = 0, culled = 0;
    uint32_t current_texture = 0xFFFFFFFF;
    GLsizei instance_count = 0;
    size_t instance_offset = 0;
    for (auto& entry : m_entities) {
        if (!is_in_frustum(entry.bbox_min, entry.bbox_max)) {
            entry.frustum_culled = true; culled++; continue;
        }
        entry.frustum_culled = false;
        if (instance_offset + 16 > m_instance_buffer.size()) break;
        memcpy(&m_instance_buffer[instance_offset], entry.model_matrix, sizeof(float) * 16);
        instance_offset += 16; instance_count++; drawn++;
        if (entry.texture_id != current_texture) {
            if (instance_count > 0 && current_texture != 0xFFFFFFFF) {
                if (real_active) real_active(GL_TEXTURE0);
                if (real_bind_tex) real_bind_tex(GL_TEXTURE_2D, current_texture);
                if (real_draw_inst && instance_count > 1) {
                    real_draw_inst(GL_TRIANGLES, entry.index_count, GL_UNSIGNED_INT,
                                   (const void*)(uintptr_t)entry.vertex_offset, instance_count);
                    batched += instance_count;
                } else if (real_draw) {
                    for (GLsizei i = 0; i < instance_count; i++)
                        real_draw(GL_TRIANGLES, entry.index_count, GL_UNSIGNED_INT,
                                  (const void*)(uintptr_t)entry.vertex_offset);
                }
                flush_err(); instance_count = 0; instance_offset = 0;
            }
            current_texture = entry.texture_id;
        }
    }
    if (instance_count > 0) {
        if (real_active) real_active(GL_TEXTURE0);
        if (real_bind_tex) real_bind_tex(GL_TEXTURE_2D, current_texture);
        if (real_draw_inst && instance_count > 1) {
            real_draw_inst(GL_TRIANGLES, 36, GL_UNSIGNED_INT, nullptr, instance_count);
            batched += instance_count;
        } else if (real_draw) {
            for (GLsizei i = 0; i < instance_count; i++)
                real_draw(GL_TRIANGLES, 36, GL_UNSIGNED_INT, nullptr);
        }
        flush_err();
    }
    m_drawn.store(drawn); m_batched.store(batched); m_culled.store(culled);
    auto& perf = perf_get_state();
    perf.entities_drawn.store(drawn);
    perf.entities_batched.store(batched);
    perf.entities_culled.store(culled);
}

Frustum extract_frustum(const float* m) {
    Frustum f;
    f.planes[0][0] = m[3]+m[0];  f.planes[0][1] = m[7]+m[4];  f.planes[0][2] = m[11]+m[8];  f.planes[0][3] = m[15]+m[12];
    f.planes[1][0] = m[3]-m[0];  f.planes[1][1] = m[7]-m[4];  f.planes[1][2] = m[11]-m[8];  f.planes[1][3] = m[15]-m[12];
    f.planes[2][0] = m[3]+m[1];  f.planes[2][1] = m[7]+m[5];  f.planes[2][2] = m[11]+m[9];  f.planes[2][3] = m[15]+m[13];
    f.planes[3][0] = m[3]-m[1];  f.planes[3][1] = m[7]-m[5];  f.planes[3][2] = m[11]-m[9];  f.planes[3][3] = m[15]-m[13];
    f.planes[4][0] = m[3]+m[2];  f.planes[4][1] = m[7]+m[6];  f.planes[4][2] = m[11]+m[10]; f.planes[4][3] = m[15]+m[14];
    f.planes[5][0] = m[3]-m[2];  f.planes[5][1] = m[7]-m[6];  f.planes[5][2] = m[11]-m[10]; f.planes[5][3] = m[15]-m[14];
    for (int i = 0; i < 6; i++) {
        float len = sqrtf(f.planes[i][0]*f.planes[i][0]+f.planes[i][1]*f.planes[i][1]+f.planes[i][2]*f.planes[i][2]);
        if (len > 0.0001f) { float inv=1.0f/len; f.planes[i][0]*=inv; f.planes[i][1]*=inv; f.planes[i][2]*=inv; f.planes[i][3]*=inv; }
    }
    return f;
}

} // namespace turbo_v1
