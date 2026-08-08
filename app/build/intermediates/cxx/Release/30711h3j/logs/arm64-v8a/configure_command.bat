@echo off
"C:\\Users\\dines\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\dines\\Documents\\01-WORK\\NOEON(The 1st AB)\\Projects\\App\\Memossist\\app\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=C:\\Users\\dines\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\dines\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\dines\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\dines\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\dines\\Documents\\01-WORK\\NOEON(The 1st AB)\\Projects\\App\\Memossist\\app\\build\\intermediates\\cxx\\Release\\30711h3j\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\dines\\Documents\\01-WORK\\NOEON(The 1st AB)\\Projects\\App\\Memossist\\app\\build\\intermediates\\cxx\\Release\\30711h3j\\obj\\arm64-v8a" ^
  "-BC:\\Users\\dines\\Documents\\01-WORK\\NOEON(The 1st AB)\\Projects\\App\\Memossist\\app\\.cxx\\Release\\30711h3j\\arm64-v8a" ^
  -GNinja ^
  "-DCMAKE_BUILD_TYPE=Release"
