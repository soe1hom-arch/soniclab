cmake_minimum_required(VERSION 3.22.1)

project("soniclab_hrtf" LANGUAGES C CXX)

include(FetchContent)

# libmysofa is the actual AES SOFA reader. Build its static target for Android
# and disable its desktop-only test programs/dependencies.
set(BUILD_TESTS OFF CACHE BOOL "Disable libmysofa tests" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build libmysofa static" FORCE)
set(BUILD_STATIC_LIBS ON CACHE BOOL "Build libmysofa static" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

FetchContent_Declare(
    libmysofa
    GIT_REPOSITORY https://github.com/hoene/libmysofa.git
    GIT_TAG v1.3.5
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(libmysofa)

# libmysofa's CMake target sets PIC from BUILD_SHARED_LIBS; explicitly keep it
# position-independent because it is linked into this JNI shared library.
set_target_properties(mysofa-static PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(
    soniclab_hrtf
    SHARED
    hrtf_engine.cpp
)

target_include_directories(
    soniclab_hrtf
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${libmysofa_BINARY_DIR}/src
)

find_library(log-lib log REQUIRED)
target_link_libraries(soniclab_hrtf PRIVATE mysofa-static ${log-lib})
