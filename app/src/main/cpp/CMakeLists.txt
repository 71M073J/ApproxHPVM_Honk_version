# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("approxhpvmdemo")

include(${PROJECT_SOURCE_DIR}/bin/${ANDROID_ABI}/lib/cmake/hpvm-tensor-rt/hpvm-tensor-rt-config.cmake)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

find_library(log-lib log)
find_library(android-lib android)

#add_subdirectory(third_party/fmt)

#
#
#ARM COMPUTE
#

add_library(native-lib SHARED native-lib.cpp)
target_include_directories(native-lib PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} include)
#target_compile_definitions(native-lib PUBLIC NDEBUG)

add_library(hpvm_c STATIC IMPORTED)
set_target_properties(hpvm_c PROPERTIES
        IMPORTED_LOCATION ${PROJECT_SOURCE_DIR}/bin/${ANDROID_ABI}/hpvm_c.linked.bc
        INTERFACE_LINK_LIBRARIES "hpvm-tensor-rt"
        )

target_link_libraries(native-lib
        ${log-lib} ${android-lib}
        hpvm-tensor-rt
        fmt
        hpvm_c
        )
