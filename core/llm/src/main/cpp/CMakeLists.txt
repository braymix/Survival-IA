cmake_minimum_required(VERSION 3.22.1)
project(survivalllm CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Percorso al submodule llama.cpp (repo_root/native/llama.cpp).
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native/llama.cpp")

if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "Submodule llama.cpp non trovato in ${LLAMA_DIR}. "
        "Esegui: git submodule update --init --depth 1 native/llama.cpp")
endif()

# Disattiva tutto ciò che non serve su Android (eseguibili, test, server, curl).
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# ARM: abilita ottimizzazioni dot-product int8 quando disponibili.
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

add_library(survivalllm SHARED llama_jni.cpp)

find_library(log-lib log)
target_link_libraries(survivalllm llama ggml ${log-lib})
