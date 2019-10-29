package com.wyh.plugin;

import com.android.SdkConstants;

import org.gradle.api.Project;

import java.io.File;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * @author WangYingHao
 * @since 2019-10-28
 */
public class LogTransform extends JavassistTransform {

    private static final String ANNOTATION_AOP = "@com.test.Aop";

    public LogTransform(Project project) {
        super(project);
    }


    @Override
    public String getName() {
        return "LogTransform";
    }

    @Override
    protected boolean isLogEnable() {
        return true;
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

    private void logMethod(CtClass ctClass) throws ClassNotFoundException, CannotCompileException {
        ctClass.defrost();
        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (CtMethod method : methods) {
            if (CtClassUtil.hasAnnotation(method, ANNOTATION_AOP)) {
                method.insertBefore("android.util.Log.d(\"" + ctClass.getSimpleName() + "\",\"LogTransform------>logMethod\");");
            }
        }
    }
}
