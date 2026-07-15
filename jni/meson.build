project('mh_drive', 'c', 'cpp',
  version : '1.0.0',
  default_options : ['warning_level=2', 'cpp_std=c++17', 'optimization=3', 'b_staticpic=true'])

add_project_arguments('-DANDROID', '-D_GNU_SOURCE', language : ['c', 'cpp'])

cc = meson.get_compiler('c')
cpp = meson.get_compiler('cpp')

# Link Android NDK standard libraries
log_dep = cc.find_library('log', required : true)
android_dep = cc.find_library('android', required : true)
dl_dep = cc.find_library('dl', required : true)

# Module A: GL4ES Translation Wrapper
shared_library('mh_drive_gl_wrapper',
  sources : [
    'src/mh_drive_gl_wrapper.cpp'
  ],
  dependencies : [log_dep, android_dep, dl_dep],
  install : true
)

# Module B: Custom Mesa / Zink Emulation Engine
shared_library('mh_drive_vulkan_mesa',
  sources : [
    'src/zink_device.c'
  ],
  dependencies : [log_dep, android_dep, dl_dep],
  install : true
)
