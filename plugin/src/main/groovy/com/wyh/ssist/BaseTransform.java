package com.wyh.ssist;

import com.android.SdkConstants;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import static com.wyh.ssist.LogUtil.println;


/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public abstract class BaseTransform extends Transform implements ITransform {

    private static final FileTime ZERO = FileTime.fromMillis(0L);

    private ClassPool mClassPool;
    private Project mProject;
    private Set<ClassPath> mClassPathSet;

    public BaseTransform(Project project) {
        this.mProject = project;
        mClassPool = ClassPool.getDefault();
        mClassPathSet = new HashSet<>();
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) {
        try {
            println("---start---");
            final Collection<TransformInput> inputs = transformInvocation.getInputs(); //输入
            final TransformOutputProvider outputProvider = transformInvocation.getOutputProvider(); //输出
            final boolean isIncremental = transformInvocation.isIncremental(); //是否是增量模式编译
            if (isIncremental) {
                println("---isIncremental---");
            } else {
                println("---isNotIncremental---");
                outputProvider.deleteAll(); //若不是增量模式编译则清空输出文件
            }
            AppExtension appExtension = (AppExtension) mProject.getProperties().get("android");
            List<File> bootClassPaths = appExtension.getBootClasspath();
            if (bootClassPaths != null) {
                for (File bootDir : bootClassPaths) {
                    final String classPath = bootDir.getAbsolutePath();
                    mClassPathSet.add(mClassPool.appendClassPath(classPath)); //类查找路径添加根目录
                    println("appendClassPath : " + classPath);
                }
            }
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    final String classPath = jarInput.getFile().getAbsolutePath();
                    mClassPathSet.add(mClassPool.insertClassPath(classPath)); //类查找路径添加每个 jar 路径
                    println("insertClassPath(jarInput) : " + classPath);
                }
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    String classPath = directoryInput.getFile().getAbsolutePath();
                    mClassPathSet.add(mClassPool.insertClassPath(classPath)); //类查找路径添加每个源码文件夹
                    println("insertClassPath(dirInput) : " + classPath);
                }
            }
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    transformInput(jarInput, outputProvider, isIncremental); //处理 jar 文件
                }
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    transformInput(directoryInput, outputProvider, isIncremental); //处理源码文件夹
                }
            }
        } catch (Exception e) {
            println(e);
        } finally {
            for (ClassPath classPath : mClassPathSet) {
                /*
                 * 移除所有的类查找路径，防止 clean Project 时发生 Failed to delete some children :
                 * This might happen because a process has files open or has its working directory set in the target directory
                 */
                mClassPool.removeClassPath(classPath);
            }
            mClassPathSet.clear();
            println("---removeAllClassPath---");
            println("---end---");
        }
    }

    /**
     * 处理源码文件夹
     *
     * @param directoryInput 输入的源码文件夹
     * @param outputProvider 输出信息
     * @param isIncremental  是否是增量模式编译
     */
    private void transformInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException {
        File dest = outputProvider.getContentLocation(
                directoryInput.getName(),
                directoryInput.getContentTypes(),
                directoryInput.getScopes(),
                Format.DIRECTORY);
        FileUtils.forceMkdir(dest);
        File directoryInputFile = directoryInput.getFile();
        final String inputClassPath = directoryInputFile.getAbsolutePath();
        final String destDirPath = dest.getAbsolutePath();
        if (isIncremental) {
            Map<File, Status> fileStatusMap = directoryInput.getChangedFiles();
            for (Map.Entry<File, Status> changedFile : fileStatusMap.entrySet()) {
                File inputFile = changedFile.getKey();
                String destFilePath = inputFile.getAbsolutePath().replace(inputClassPath, destDirPath);
                File destFile = new File(destFilePath);
                switch (changedFile.getValue()) {
                    case NOTCHANGED:
                        break;
                    case REMOVED:
                        if (destFile.exists()) {
                            FileUtils.forceDelete(destFile);
                        }
                        break;
                    case ADDED:
                    case CHANGED:
                        FileUtils.touch(destFile);
                        transformFileInner(inputClassPath, directoryInputFile, inputFile, destFile);
                        break;
                }
            }
        } else {
            if (directoryInputFile.isDirectory()) {
                for (File inputFile : com.android.utils.FileUtils.getAllFiles(directoryInputFile)) {
                    String inputFilePath = inputFile.getAbsolutePath();
                    transformFileInner(inputClassPath, directoryInputFile, inputFile,
                            new File(inputFilePath.replace(inputClassPath, destDirPath)));
                }
            }
        }
    }

    /**
     * 进一步处理源码文件夹里的某个类文件
     *
     * @param inputClassPath 所在源码文件夹的类查找路径
     * @param dirFile        所在源码文件夹
     * @param inputFile      类文件
     * @param outputFile     处理后的输出文件
     */
    private void transformFileInner(String inputClassPath, File dirFile, File inputFile, File outputFile) {
        if (tryTransformDirClassFile(dirFile, inputFile)) {
            try {
                CtClass ctClass = getCtClass(inputClassPath, inputFile);
                println("transformDirClassFile:" + ctClass.getName());
                transformDirClassFile(ctClass);
                ctClass.writeFile(inputClassPath);
                ctClass.detach();
            } catch (Exception e) {
                println(e);
            }
        }
        try {
            FileUtils.copyFile(inputFile, outputFile);
        } catch (IOException e) {
            println(e);
        }
    }

    /**
     * 处理 jar 文件
     *
     * @param jarInput       输入的 jar
     * @param outputProvider 输出信息
     * @param isIncremental  是否是增量模式编译
     */
    private void transformInput(JarInput jarInput, TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException {
        String jarName = jarInput.getName();
        String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4);
        }
        File dest = outputProvider.getContentLocation(
                jarName + md5Name,
                jarInput.getContentTypes(),
                jarInput.getScopes(),
                Format.JAR);
        if (isIncremental) {
            //todo 记录 jar 处理数量打印
            println("transformInput(jarInput)：jarName=" + jarName + " state：" + jarInput.getStatus().toString());
            switch (jarInput.getStatus()) {
                case NOTCHANGED:
                    break;
                case ADDED:
                case CHANGED:
                    transformJarInner(jarInput.getFile(), dest);
                    break;
                case REMOVED:
                    if (dest.exists()) {
                        FileUtils.forceDelete(dest);
                    }
                    break;
            }
        } else {
            transformJarInner(jarInput.getFile(), dest);
        }
    }

    /**
     * 进一步处理 jar 文件
     *
     * @param inputJarFile  要处理的 jar 文件
     * @param outputJarFile 处理后的输出文件
     */
    private void transformJarInner(File inputJarFile, File outputJarFile) {
        if (tryTransformJarFile(inputJarFile)) {
            try {
                ZipFile inputZip = new ZipFile(inputJarFile);
                ZipOutputStream outputZip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputJarFile.toPath())));
                Enumeration inEntries = inputZip.entries();
                while (inEntries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) inEntries.nextElement();
                    InputStream inputStream = inputZip.getInputStream(entry);
                    InputStream originalFile = new BufferedInputStream(inputStream);
                    ZipEntry outEntry = new ZipEntry(entry.getName());
                    byte[] newEntryContent;
                    if (tryTransformJarClassFile(inputJarFile, outEntry.getName())) {
                        try {
                            CtClass ctClass = mClassPool.makeClass(originalFile, false);
                            println("transformJarClassFile:" + ctClass.getName());
                            transformJarClassFile(ctClass);
                            newEntryContent = ctClass.toBytecode();
                        } catch (RuntimeException e) {
                            println(e);
                            newEntryContent = IOUtils.toByteArray(originalFile);
                        }
                    } else {
                        newEntryContent = IOUtils.toByteArray(originalFile);
                    }
                    IOUtil.closeQuietly(originalFile);
                    IOUtil.closeQuietly(inputStream);
                    CRC32 crc32 = new CRC32();
                    crc32.update(newEntryContent);
                    outEntry.setCrc(crc32.getValue());
                    outEntry.setMethod(0);
                    outEntry.setSize((long) newEntryContent.length);
                    outEntry.setCompressedSize((long) newEntryContent.length);
                    outEntry.setLastAccessTime(ZERO);
                    outEntry.setLastModifiedTime(ZERO);
                    outEntry.setCreationTime(ZERO);
                    outputZip.putNextEntry(outEntry);
                    outputZip.write(newEntryContent);
                    outputZip.closeEntry();
                }
                outputZip.flush();
                IOUtil.closeQuietly(outputZip);
                IOUtil.closeQuietly(inputZip);
            } catch (Exception e) {
                println(e);
            }
        } else {
            try {
                FileUtils.copyFile(inputJarFile, outputJarFile);
            } catch (IOException e) {
                println(e);
            }
        }
    }

    /**
     * 获取 CtClass
     *
     * @param inputClassPath 所在类查找路径
     * @param inputClassFile 类文件
     */
    private CtClass getCtClass(String inputClassPath, File inputClassFile) {
        String classFilePath = inputClassFile.getAbsolutePath();
        String className =
                classFilePath.replace(inputClassPath, "")
                        .replace(SdkConstants.DOT_CLASS, "")
                        .replace("\\", ".")
                        .replace("/", ".");
        if (className.startsWith(".")) {
            className = className.substring(1);
        }
        return getCtClass(className);
    }

    /**
     * 获取 CtClass
     *
     * @param className 类名
     */
    private CtClass getCtClass(String className) {
        try {
            return mClassPool.getCtClass(className);
        } catch (NotFoundException e) {
            println(e);
        }
        return null;
    }

}
