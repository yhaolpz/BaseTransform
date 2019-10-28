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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public abstract class JavassistTransform extends Transform {

    protected ClassPool classPool;
    private ExecutorService transformClassFileExecutor;
    private Project project;

    public JavassistTransform(Project project) {
        this.project = project;
        classPool = ClassPool.getDefault();
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
    public void transform(TransformInvocation transformInvocation) {
        try {
            Collection<TransformInput> inputs = transformInvocation.getInputs();
            TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
            boolean isIncremental = transformInvocation.isIncremental();
            if (!isIncremental) {
                outputProvider.deleteAll();
            }
            AppExtension appExtension = (AppExtension) project.getProperties().get("android");
            List<File> bootClassPaths = appExtension.getBootClasspath();
            if (bootClassPaths != null) {
                for (File bootDir : bootClassPaths) {
                    classPool.appendClassPath(bootDir.getAbsolutePath());
                }
            }
            classPool.appendClassPath(new ClassClassPath(this.getClass()));
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    Status status = jarInput.getStatus();

                    // 重命名输出文件（同目录copyFile会冲突）
                    String jarName = jarInput.getName();
                    String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                    if (jarName.endsWith(".jar")) {
                        jarName = jarName.substring(0, jarName.length() - 4);
                    }
                    File dest = outputProvider.getContentLocation(jarName + md5Name, jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                    final String inputClassPath = jarInput.getFile().getAbsolutePath();
                    classPool.insertClassPath(inputClassPath);
                    if (isIncremental) {
                        switch (status) {
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
                    classPool.insertClassPath(inputClassPath);
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
                                    transformFileInner(inputClassPath, inputFile, destFile);
                                    break;
                            }
                        }
                    } else {
                        if (directoryInput.getFile().isDirectory()) {
                            for (File inputFile : com.android.utils.FileUtils.getAllFiles(directoryInput.getFile())) {
                                String inputFilePath = inputFile.getAbsolutePath();
                                transformFileInner(inputClassPath, inputFile,
                                        new File(inputFilePath.replace(inputClassPath, destDirPath)));
                            }
                        }
                    }
                }
            }
            transformClassFileExecutor.shutdown();
            transformClassFileExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        } catch (Exception e) {
            println(e);
        }
    }


    private void transformFileInner(String inputClassPath, File inputFile, File outputFile) {
//        transformClassFileExecutor.execute(() -> {
            String inputFilePath = inputFile.getAbsolutePath();
            if (isClassFile(inputFilePath)) {
                try {
                    printlnIfDebug("transformFileInner:" + inputFile.getAbsolutePath());
                    transformClassFile(inputClassPath, inputFile, outputFile);
                } catch (Exception e) {
                    println(e);
                }
            }
            try {
                FileUtils.copyFile(inputFile, outputFile);
            } catch (IOException e) {
                println(e);
            }
//        });
    }

    private void transformJarInner(File inputFile, File outputFile) {
//        transformClassFileExecutor.execute(() -> {
            try {
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
            } catch (Exception e) {
                println(e);
            } finally {
                try {
                    FileUtils.copyFile(inputFile, outputFile);
                } catch (IOException e) {
                    println(e);
                }
            }
//        });
    }

    private void printlnIfDebug(Object log) {
        if (isLogEnable()) {
            println(log);
        }
    }

    private void println(Object log) {
        LogUtil.println(log);
    }


    private boolean isClassFile(String filePath) {
        return filePath.endsWith(SdkConstants.DOT_CLASS)
                && !filePath.contains("R$")
                && !filePath.contains("R.class")
                && !filePath.contains("BuildConfig.class");
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
            return classPool.getCtClass(className);
        } catch (NotFoundException e) {
            println(e);
        }
        return null;
    }


    protected boolean isLogEnable() {
        return false;
    }

    protected abstract void transformClassFile(String inputClassPath, File inputFile, File outputFile) throws Exception;

    protected abstract void transformJarClassFile(String className) throws Exception;
}
