package com.wyh.ssist;

import java.io.File;

import javassist.CtClass;

/**
 * @author WangYingHao
 * @since 2019-10-29
 */
public interface ITransform {

    /**
     * 是否需要转换为 CtClass 进一步处理
     *
     * @param dirFile   所属代码目录
     * @param classFile 待处理字节码文件
     * @return 返回 true 才会将此类文件转换为 CtClass 进一步调用{@link #transformDirClassFile}
     * 返回 false 则直接跳过此类文件不处理
     */
    boolean tryTransformDirClassFile(File dirFile, File classFile);

    /**
     * 处理代码目录中的类文件
     *
     * @param ctClass 该类文件 CtClass
     */
    void transformDirClassFile(CtClass ctClass) throws Exception;


    /**
     * 是否需要解压 jar 处理其中的类文件
     *
     * @param jarFile jar 文件
     * @return 返回 true 才会解压 jar 并进一步调用{@link #tryTransformJarClassFile}
     * 返回 false 则直接跳过此 jar 文件不处理
     */
    boolean tryTransformJarFile(File jarFile);

    /**
     * 是否需要转换为 CtClass 进一步处理
     *
     * @param jarFile   所属 jar 文件
     * @param className jar 中待处理的字节码文件
     * @return 返回 true 才会将此类文件转换为 CtClass 进一步调用{@link #transformJarClassFile}
     * 返回 false 则直接跳过此类文件不处理
     */
    boolean tryTransformJarClassFile(File jarFile, String className);

    /**
     * 处理 jar 文件中的类文件
     *
     * @param ctClass 该类文件 CtClass
     */
    void transformJarClassFile(CtClass ctClass) throws Exception;
}
