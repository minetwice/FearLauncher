find_package(Git)

if(GIT_EXECUTABLE)
  get_filename_component(SRC_DIR ${SRC} DIRECTORY)
  # Generate a git-describe version string from Git repository tags
  execute_process(
    COMMAND ${GIT_EXECUTABLE} describe --tags --dirty --match "v*"
    WORKING_DIRECTORY ${SRC_DIR}
    OUTPUT_VARIABLE GIT_DESCRIBE_VERSION
    RESULT_VARIABLE GIT_DESCRIBE_ERROR_CODE
    OUTPUT_STRIP_TRAILING_WHITESPACE
  )
  if(NOT GIT_DESCRIBE_ERROR_CODE)
    set(RENDERER_VERSION ${GIT_DESCRIBE_VERSION})
  endif()
endif()

if(GIT_EXECUTABLE AND NOT DEFINED RENDERER_VERSION)
  message("Getting renderer version from commit hash...")
  get_filename_component(SRC_DIR ${SRC} DIRECTORY)
  execute_process(
    COMMAND ${GIT_EXECUTABLE} rev-parse --short HEAD
    WORKING_DIRECTORY ${SRC_DIR}
    OUTPUT_VARIABLE GIT_COMMIT_HASH
    RESULT_VARIABLE GIT_COMMIT_HASH_ERROR_CODE
    OUTPUT_STRIP_TRAILING_WHITESPACE
  )
  if(NOT GIT_COMMIT_HASH_ERROR_CODE)
    set(RENDERER_VERSION v0.0.0-${GIT_COMMIT_HASH})
  endif()
endif()

# Final fallback: Just use a bogus version string that is semantically older
# than anything else and spit out a warning to the developer.
if(NOT DEFINED RENDERER_VERSION)
  set(RENDERER_VERSION v0.0.0-dev)
  message(WARNING "Failed to determine RENDERER_VERSION from Git tags nor commits. Using default version \"${RENDERER_VERSION}\".")
endif()

message("RENDERER_VERSION : ${RENDERER_VERSION}")

configure_file(${SRC} ${DST} @ONLY)