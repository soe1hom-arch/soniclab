# Copyright 2026 soe1hom-arch
# SPDX-License-Identifier: Apache-2.0

cmake_minimum_required(VERSION 3.22.1)

project(soniclab_oboe LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(soniclab_oboe SHARED oboe_engine.cpp)

target_compile_options(soniclab_oboe PRIVATE -Wall -Wextra)
target_include_directories(soniclab_oboe PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(soniclab_oboe oboe::oboe log android)
