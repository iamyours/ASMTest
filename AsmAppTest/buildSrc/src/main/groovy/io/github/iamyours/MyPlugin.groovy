package io.github.iamyours

import org.gradle.api.Plugin
import org.gradle.api.Project

class MyPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println("============")
        project.android.registerTransform(new MethodLogTransform())
    }
}
