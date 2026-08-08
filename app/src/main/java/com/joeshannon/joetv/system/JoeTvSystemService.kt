package com.joeshannon.joetv.system

import com.joeshannon.joetv.IJoeTvSystemService

class JoeTvSystemService : IJoeTvSystemService.Stub() {

    override fun runCommand(command: String): String {
        return try {
            val process = ProcessBuilder(
                "sh",
                "-c",
                command
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                output.ifBlank { "Success" }
            } else {
                "Command failed ($exitCode): ${
                    output.ifBlank { "Unknown error" }
                }"
            }
        } catch (exception: Exception) {
            "Error: ${exception.message ?: "Unknown error"}"
        }
    }
}