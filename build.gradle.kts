plugins {
    id("java")
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = "com.harmless004.aicopilot"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity("2023.1.5")
        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }

    // HTTP client for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Utilities
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-codec:commons-codec:1.15")

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.1.1")
}

// Set the JVM language level used to build the project. Use Java 17 for compatibility.
kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        version = "1.0.0"

        // Plugin Details
        name = "AI Copilot"
        description = """
            AI-powered code completion assistant for IntelliJ IDEA that provides intelligent code suggestions
            using OpenAI GPT or Anthropic Claude.
            
            Features:
            • Real-time code completion with AI assistance
            • Context-aware suggestions based on your current code
            • Support for multiple programming languages
            • Comment-to-code generation
            • Environment variable based configuration for security
            
            Configuration:
            Set environment variables:
            - OPENAI_API_KEY for OpenAI GPT-4
            - ANTHROPIC_API_KEY for Anthropic Claude
            - AI_PROVIDER=OPENAI or AI_PROVIDER=CLAUDE (optional, defaults to OpenAI)
        """

        changeNotes = """
            Version 1.0.0:
            <ul>
                <li>Initial release</li>
                <li>OpenAI GPT-4 integration</li>
                <li>Anthropic Claude integration</li>
                <li>Real-time code completion</li>
                <li>Context-aware suggestions</li>
                <li>Multi-language support</li>
                <li>Environment variable configuration</li>
                <li>Response caching</li>
            </ul>
        """

        // Plugin Compatibility
        ideaVersion {
            sinceBuild = "231"
            untilBuild = "241.*"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }

    test {
        useJUnit()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Configure changelog plugin
changelog {
    groups.empty()
    repositoryUrl = "https://github.com/harmless004/intellij-ai-copilot"
}