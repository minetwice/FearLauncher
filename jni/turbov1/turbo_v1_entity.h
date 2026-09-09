#ifndef TURBO_V1_ENTITY_H
#define TURBO_V1_ENTITY_H

#include "turbo_v1_core.h"
#include <vector>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <unordered_map>

namespace turbo_v1 {

// ============================================================================
// Indus2.0 Entity Batch Renderer
// Handles entity rendering with instancing, frustum culling, and occlusion
// queries to dramatically reduce draw calls and boost FPS with many entities
// ============================================================================

struct EntityBatchEntry {
    uint32_t entity_id;
    float    model_matrix[16];   // world transform
    float    bbox_min[3];         // bounding box min for culling
    float    bbox_max[3];         // bounding box max for culling
    uint32_t texture_id;          // entity texture atlas page
    uint32_t vertex_offset;       // offset into shared VBO
    uint32_t index_count;         // number of indices
    bool     visible;
    bool     frustum_culled;
};

struct EntityBatch {
    std::vector<EntityBatchEntry> entries;
    std::vector<float>            instance_data;   // packed instance matrices
    uint32_t                      instance_count;
    uint32_t                      texture_atlas_id;
    GLenum                        draw_mode;
    bool                          dirty;
};

// Frustum planes for culling (Ax + By + Cz + D = 0)
struct Frustum {
    float planes[6][4]; // 6 planes, each with 4 coefficients
};

class EntityManager {
public:
    static EntityManager& instance();

    void init();
    void shutdown();

    void begin_frame(const Frustum& frustum);
    void end_frame();

    uint32_t register_entity(uint32_t entity_id, const float* model_matrix,
                              const float* bbox_min, const float* bbox_max,
                              uint32_t texture_id, uint32_t vertex_offset,
                              uint32_t index_count);
    void unregister_entity(uint32_t entity_id);
    void update_entity_transform(uint32_t entity_id, const float* model_matrix);

    void submit_batched_draws();

    bool is_in_frustum(const float* bbox_min, const float* bbox_max) const;

    int get_drawn_count() const  { return m_drawn.load(); }
    int get_batched_count() const { return m_batched.load(); }
    int get_culled_count() const { return m_culled.load(); }

private:
    EntityManager() = default;

    std::vector<EntityBatchEntry> m_entities;
    std::unordered_map<uint32_t, size_t> m_entity_index;
    Frustum m_current_frustum{};
    std::mutex m_mutex;

    std::atomic<int> m_drawn{0};
    std::atomic<int> m_batched{0};
    std::atomic<int> m_culled{0};
    std::atomic<bool> m_initialized{false};

    std::vector<float> m_instance_buffer;
    size_t m_instance_buffer_capacity = 0;
};

Frustum extract_frustum(const float* proj_view_matrix);

} // namespace turbo_v1

#endif // TURBO_V1_ENTITY_H
