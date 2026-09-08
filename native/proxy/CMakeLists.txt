cmake_minimum_required(VERSION 3.21)
project(NimbusProxy LANGUAGES CXX)
if(NOT WIN32 OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "Nimbus target agent requires Windows x64")
endif()
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
add_library(nimbus_proxy SHARED
    loader.cpp native_bridge.cpp env.cpp random_name.cpp classfile.cpp class_edit.cpp
    trampolines.cpp relay_handler.cpp connection_hook.cpp world_cache.cpp scoreboard_snapshot.cpp player_snapshot.cpp b_server.cpp
)
target_include_directories(nimbus_proxy PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_definitions(nimbus_proxy PRIVATE WIN_X64 NOMINMAX _CRT_SECURE_NO_WARNINGS)
if(MSVC)
    target_compile_options(nimbus_proxy PRIVATE /EHsc /utf-8 $<$<CONFIG:Release>:/O2>)
endif()
target_link_libraries(nimbus_proxy PRIVATE psapi ws2_32 kernel32 user32 advapi32 bcrypt)
set_target_properties(nimbus_proxy PROPERTIES OUTPUT_NAME "nimbus_proxy")
