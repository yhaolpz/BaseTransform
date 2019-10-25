package com.wyh.plugin;

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

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public abstract class JavassistTransform extends Transform {

    protected ClassPool classPool;
    private boolean debug = true;
    private ExecutorService transformJarExecutor;
    private ExecutorService transformClassFileExecutor;
    private Project project;

    public JavassistTransform(Project project) {
        this.project = project;
        classPool = ClassPool.getDefault();
        transformJarExecutor = Executors.newFixedThreadPool(20);
        transformClassFileExecutor = Executors.newFixedThreadPool(20);
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
    public void transform(TransformInvocation transformInvocation) throws IOException {
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        if (!isIncremental) {
            outputProvider.deleteAll();
        }
        //todo bootClasspath
        ///Users/meitu/Library/Android/sdk/platforms/android-28/android.jar
        AppExtension appExtension = (AppExtension) project.getProperties().get("android");
        String bootClassPath = appExtension.getBootClasspath().toString();
        //todo 这个顺序为什么在线程池任务后边？
        println("bootClassPath:" + bootClassPath);
        insertClassPath(bootClassPath);
        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                Status status = jarInput.getStatus();
                File dest = outputProvider.getContentLocation(
                        jarInput.getName(),
                        jarInput.getContentTypes(),
                        jarInput.getScopes(),
                        Format.JAR);
                final String inputClassPath = jarInput.getFile().getAbsolutePath();
                if (isIncremental) {
                    switch (status) {
                        case NOTCHANGED:
                            break;
                        case ADDED:
                        case CHANGED:
                            transformJarExecutor.execute(() -> transformJarInner(inputClassPath, jarInput.getFile(), dest));
                            break;
                        case REMOVED:
                            if (dest.exists()) {
                                FileUtils.forceDelete(dest);
                            }
                            break;
                    }
                } else {
                    transformJarExecutor.execute(() -> transformJarInner(inputClassPath, jarInput.getFile(), dest));
                }
            }
        }
        transformJarExecutor.shutdown();
        try {
            transformJarExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (TransformInput input : inputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File dest = outputProvider.getContentLocation(
                        directoryInput.getName(),
                        directoryInput.getContentTypes(),
                        directoryInput.getScopes(),
                        Format.DIRECTORY);
                FileUtils.forceMkdir(dest);
                final String inputClassPath = directoryInput.getFile().getAbsolutePath();
                final String destDirPath = dest.getAbsolutePath();
                insertClassPath(inputClassPath);
                if (isIncremental) {
                    Map<File, Status> fileStatusMap = directoryInput.getChangedFiles();
                    for (Map.Entry<File, Status> changedFile : fileStatusMap.entrySet()) {
                        Status status = changedFile.getValue();
                        File inputFile = changedFile.getKey();
                        String destFilePath = inputFile.getAbsolutePath().replace(inputClassPath, destDirPath);
                        File destFile = new File(destFilePath);
                        switch (status) {
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
                                transformClassFileExecutor.execute(() ->
                                        transformFileInner(inputClassPath, inputFile, destFile));
                                break;
                        }
                    }
                } else {
                    if (directoryInput.getFile().isDirectory()) {
                        for (File inputFile : com.android.utils.FileUtils.getAllFiles(directoryInput.getFile())) {
                            String inputFilePath = inputFile.getAbsolutePath();
                            transformClassFileExecutor.execute(() ->
                                    transformFileInner(inputClassPath, inputFile,
                                            new File(inputFilePath.replace(inputClassPath, destDirPath))));
                        }
                    }
                }
            }
        }
        transformClassFileExecutor.shutdown();
        try {
            transformClassFileExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void transformFileInner(String inputClassPath, File inputFile, File outputFile) {
        String inputFilePath = inputFile.getAbsolutePath();
        if (isClassFile(inputFilePath)) {
            try {
                printlnIfDebug("transformFileInner:" + inputFile.getAbsolutePath());
                transformClassFile(inputClassPath, inputFile, outputFile);
            } catch (Exception e) {
                println("Exception!!! transformFileInner fail," + e.toString());
            }
        }
        try {
            FileUtils.copyFile(inputFile, outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void transformJarInner(String inputClassPath, File inputFile, File outputFile) {
        try {
            ClassPath classPath = insertClassPath(inputClassPath);
            if (classPath != null) {
                printlnIfDebug("transformJarInner:" + inputFile.getAbsolutePath());
                ZipFile inputZip = new ZipFile(inputFile);
                Enumeration inEntries = inputZip.entries();
                while (inEntries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) inEntries.nextElement();
                    String className = entry.getName();
                    if (!isClassFile(className)) {
                        continue;
                    }
                    className = className.
                            replace(SdkConstants.DOT_CLASS, "")
                            .replace("\\", ".")
                            .replace("/", ".");
                    transformJarClassFile(className);
                }
            }
        } catch (Exception e) {
            println("Exception!!! transformJarInner fail," + e.toString());
        } finally {
            try {
                FileUtils.copyFile(inputFile, outputFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isClassFile(String filePath) {
        return filePath.endsWith(SdkConstants.DOT_CLASS)
                && !filePath.contains("R$")
                && !filePath.contains("R.class")
                && !filePath.contains("BuildConfig.class");
    }

    private ClassPath insertClassPath(String path) {
        try {
            return classPool.insertClassPath(path);
        } catch (NotFoundException e) {
            printlnIfDebug("insertClassPath fail," + e.toString());
            return null;
        }
    }

    protected CtClass getCtClass(String inputClassPath, File classFile) {
        String classFilePath = classFile.getAbsolutePath();
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


    protected CtClass getCtClass(String className) {
        try {
            CtClass ctClass = classPool.getCtClass(className);
            return ctClass;
        } catch (NotFoundException e) {
            println("getCtClass fail," + e.toString());
        }
        return null;
    }


    protected void printlnIfDebug(Object log) {
        if (isLogEnable()) {
            println(log);
        }
    }

    protected void println(Object log) {
        System.out.println(LogUtil.toString(log));
    }

    protected boolean isLogEnable() {
        return false;
    }

    protected void transformClassFile(String inputClassPath, File inputFile, File outputFile) throws Exception {

    }

    protected void transformJarClassFile(String className) throws Exception {

    }
}
