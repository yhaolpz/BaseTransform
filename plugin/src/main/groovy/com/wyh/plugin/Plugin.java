package com.wyh.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;

import org.gradle.api.Project;

import java.util.Collections;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class Plugin implements org.gradle.api.Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            AppExtension appExtension = (AppExtension) project.getProperties().get("android");
            appExtension.registerTransform(new LogTransform(project), Collections.EMPTY_LIST);
            System.out.println("Plugin apply and registerTransform");
        } else {
            System.out.println("error! Plug in must be applied in app");
        }
    }
}
