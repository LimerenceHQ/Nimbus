cmake_minimum_required(VERSION 3.21)
project(NimbusGateway LANGUAGES C CXX)

if(NOT WIN32 OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "Nimbus Gateway currently supports Windows x64 only")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

set(GATEWAY_NATIVE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/native")
set(GATEWAY_INJECTOR_DIR "${CMAKE_CURRENT_SOURCE_DIR}/injector")

add_library(nimbus_gateway_bridge SHARED
    native/ReflectiveLoader.c
    native/loader.cpp
    native/env.cpp
    native/gateway_state.cpp
    native/random_name.cpp
    native/classfile.cpp
    native/class_edit.cpp
    native/trampolines.cpp
    native/relay_handler.cpp
    native/connection_hook.cpp
    native/world_cache.cpp
    native/b_server.cpp
)
target_include_directories(nimbus_gateway_bridge PRIVATE
    "${GATEWAY_NATIVE_DIR}"
    "${GATEWAY_NATIVE_DIR}/include"
)
target_compile_definitions(nimbus_gateway_bridge PRIVATE
    REFLECTIVEDLLINJECTION_CUSTOM_DLLMAIN
    WIN_X64
    WIN32_LEAN_AND_MEAN
    NOMINMAX
    _CRT_SECURE_NO_WARNINGS
)
target_compile_options(nimbus_gateway_bridge PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:/EHsc>
    $<$<CONFIG:Release>:/O2>
)
target_link_libraries(nimbus_gateway_bridge PRIVATE
    psapi ws2_32 kernel32 user32 advapi32
)
set_target_properties(nimbus_gateway_bridge PROPERTIES
    OUTPUT_NAME "nimbus-gateway-bridge"
)

add_executable(nimbus_gateway_host
    injector/host_main.cpp
    injector/Inject.c
    injector/LoadLibraryR.c
    injector/GetProcAddressR.c
)
target_include_directories(nimbus_gateway_host PRIVATE
    "${GATEWAY_INJECTOR_DIR}"
    "${GATEWAY_NATIVE_DIR}"
)
target_compile_definitions(nimbus_gateway_host PRIVATE
    WIN_X64
    WIN32_LEAN_AND_MEAN
    NOMINMAX
    _CRT_SECURE_NO_WARNINGS
)
target_compile_options(nimbus_gateway_host PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:/EHsc>
    $<$<CONFIG:Release>:/O2>
)
target_link_libraries(nimbus_gateway_host PRIVATE
    advapi32 kernel32 user32
)
set_target_properties(nimbus_gateway_host PROPERTIES
    OUTPUT_NAME "nimbus-gateway-host"
)

