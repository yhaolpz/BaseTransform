package com.wyh.plugin;


import org.gradle.api.Project;

import java.io.File;

import javassist.CtClass;
import javassist.CtMethod;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public class CustomTransform extends JavassistTransform {

    public CustomTransform(Project project) {
        super(project);
    }

    @Override
    public String getName() {
        return "CustomTransform";
    }

    @Override
    protected boolean isLogEnable() {
        return true;
    }

    @Override
    protected void transformClassFile(String inputClassPath, File inputFile, File outputFile) throws Exception {
        StringBuilder builder = new StringBuilder();
        CtClass ctClass = getCtClass(inputClassPath, inputFile);
        CtMethod[] methods = ctClass.getMethods();
        builder.append("CtMethod:\n");
        for (CtMethod method : methods) {
            builder.append(method.getName() + " ");
            builder.append("Annotations:" + LogUtil.toString(method.getAnnotations()));
            builder.append("\n");
        }
//        CtClass[] interfaces = ctClass.getInterfaces();
//        println("transformClassFile inputFile:" + inputFile.getName());
//        for (CtClass inter : interfaces) {
//            println("interfaces CtClass:" + inter.getName());
//        }
//        ctClass.defrost();
//        ctClass.detach();
//        println("transformClassFile  ctClass.writeFile()");
        builder.append("\n");
//        println(builder.toString());
        ctClass.writeFile();
    }

    @Override
    protected void transformJarClassFile(String className) throws Exception {
        CtClass ctClass = getCtClass(className);
        ctClass.writeFile();
    }

}
