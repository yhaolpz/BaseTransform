package com.example.plugin;

import com.android.SdkConstants;
import com.wyh.ssist.BaseTransform;
import com.wyh.ssist.CtClassUtil;

import org.gradle.api.Project;

import java.io.File;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * @author WangYingHao
 * @since 2019-10-28
 */
public class LogTransform extends BaseTransform {

    private static final String ANNOTATION_AOP = "@com.example.mylibrary.Aop";

    public LogTransform(Project project) {
        super(project);
    }


    @Override
    public String getName() {
        return "LogTransform";
    }


    @Override
    public boolean tryTransformDirClassFile(File dirFile, File classFile) {
        final String filePath = classFile.getAbsolutePath();
        return filePath.endsWith(SdkConstants.DOT_CLASS)
                && !filePath.contains("R$")
                && !filePath.contains("R.class")
                && !filePath.contains("BuildConfig.class");
    }

    @Override
    public void transformDirClassFile(CtClass ctClass) throws Exception {
        logMethod(ctClass);
    }

    @Override
    public boolean tryTransformJarFile(File jarFile) {
        return true;
    }

    @Override
    public boolean tryTransformJarClassFile(File jarFile, String className) {
        return jarFile.getAbsolutePath().contains("intermediates")
                && className.endsWith(SdkConstants.DOT_CLASS)
                && !className.contains("R$")
                && !className.contains("R.class")
                && !className.contains("BuildConfig.class");
    }

    @Override
    public void transformJarClassFile(CtClass ctClass) throws Exception {
        logMethod(ctClass);
    }

    private void logMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        ctClass.defrost();
        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (CtMethod method : methods) {
            if (CtClassUtil.hasAnnotation(method, ANNOTATION_AOP)) {
                method.addLocalVariable("_LogTransform_method_time", CtClass.longType);
                method.insertBefore("_LogTransform_method_time = java.lang.System.currentTimeMillis();");
                method.insertAfter("_LogTransform_method_time = java.lang.System.currentTimeMillis()-_LogTransform_method_time;");
                method.insertAfter("android.util.Log.d(\"[" + ctClass.getSimpleName() + "]:" + method.getName() + "\"," +
                        "\"time:\"+_LogTransform_method_time);");
            }
        }
    }
}
