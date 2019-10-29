package com.wyh.plugin;

import java.io.File;

import javassist.CtClass;

/**
 * @author WangYingHao
 * @since 2019-10-29
 */
public interface IJavassistTransform {

    /**
     * 是否需要转换为 CtClass 进一步处理
     *
     * @param dirFile   所属代码目录
     * @param classFile 待处理字节码文件
     * @see #transformDirClassFile
     */
    boolean tryTransformDirClassFile(File dirFile, File classFile);

    /**
     * 处理代码目录中的 CtClass
     */
    void transformDirClassFile(CtClass ctClass) throws Exception;

    /**
     * 是否需要转换为 CtClass 进一步处理
     *
     * @param jarFile   所属jar文件
     * @param className jar中待处理的字节码文件
     * @see #transformJarClassFile
     */
    boolean tryTransformJarClassFile(File jarFile, String className);

    /**
     * 处理jar文件中的 CtClass
     */
    void transformJarClassFile(CtClass ctClass) throws Exception;
}
