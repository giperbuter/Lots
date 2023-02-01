
plugins {
    application
}

repositories {
    // maven {
    //     name = "eldonexus"
    //     url = uri("https://eldonexus.de/repository/maven-releases/")
    // }   
    maven { 
        url = uri("https://repo.papermc.io/repository/maven-public/") 
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT")
    // compileOnly("com.github.yannicklamprecht:worldborderapi:1.180.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// application {
//     // Define the main class for the application.
//     mainClass.set("lots.App")
// }