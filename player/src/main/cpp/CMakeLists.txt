cmake_minimum_required(VERSION 3.22.1)
project(soniclab_hrtf)

add_library(soniclab_hrtf SHARED
    hrtf_engine.cpp
)

target_include_directories(soniclab_hrtf PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(soniclab_hrtf
    android
    log
)
