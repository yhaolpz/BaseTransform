package com.wyh.plugin;

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

    private static final String ANNOTATION_AOP = "@com.wyh.test.Aop";

    public LogTransform(Project project) {
        super(project);
    }

    @Override
    protected void transformClassFile(String inputClassPath, File inputFile, File outputFile) throws Exception {
        CtClass ctClass = getCtClass(inputClassPath, inputFile);
        logMethod(ctClass);
        ctClass.writeFile(inputClassPath);
        ctClass.detach();
    }

    @Override
    protected void transformJarClassFile(String className) throws Exception {
        CtClass ctClass = getCtClass(className);
//        logMethod(ctClass);
        ctClass.writeFile();
        ctClass.detach();
    }

    @Override
    public String getName() {
        return "LogTransform";
    }


    private void logMethod(CtClass ctClass) throws ClassNotFoundException, CannotCompileException {
        ctClass.defrost();
        CtMethod[] methods = ctClass.getMethods();
        for (CtMethod method : methods) {
            if (CtClassUtil.hasAnnotation(method, ANNOTATION_AOP)) {
                method.insertBefore("android.util.Log.d(\"" + ctClass.getSimpleName() + "\",\"logMethod\");");
            }
        }
    }
}
