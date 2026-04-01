cmake_minimum_required(VERSION 3.22.1)
project(pocket_whisper_jni CXX)

# Build whisper.cpp as a static lib (no examples/tests); disable OpenMP on Android to avoid extra NDK deps.
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
        GIT_TAG v1.8.4
        GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whispercpp)

find_library(log-lib log)

add_library(pocket_whisper_jni SHARED whisper_jni.cpp)
target_compile_features(pocket_whisper_jni PRIVATE cxx_std_17)

target_include_directories(pocket_whisper_jni PRIVATE
        ${whispercpp_SOURCE_DIR}/include
)

target_link_libraries(pocket_whisper_jni PRIVATE whisper ${log-lib})
