cmake_minimum_required(VERSION 3.22.1)

project("pocket_llm")

add_library(
        pocket_llm
        SHARED
        native_llm.cpp
)

target_include_directories(
        pocket_llm
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama_include
)

# Link against prebuilt libllama.so
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so
)

# Link against prebuilt libggml.so
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so
)

# Link against prebuilt libggml-cpu.so
add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so
)

# Link against prebuilt libggml-base.so
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so
)

find_library(
        log-lib
        log
)

# Enable OpenMP support
find_package(OpenMP REQUIRED)

target_link_libraries(
        pocket_llm
        llama
        ggml
        ggml-cpu
        ggml-base
        OpenMP::OpenMP_CXX
        ${log-lib}
)
