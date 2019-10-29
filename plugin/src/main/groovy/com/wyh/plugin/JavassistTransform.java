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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import static com.wyh.plugin.LogUtil.println;

/**
 * @author WangYingHao
 * @since 2019-10-23
 */
public abstract class JavassistTransform extends Transform implements IJavassistTransform {

    private static final FileTime ZERO = FileTime.fromMillis(0L);


    private ClassPool classPool;
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
            final Collection<TransformInput> inputs = transformInvocation.getInputs();
            final TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
            final boolean isIncremental = transformInvocation.isIncremental();
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
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    final String inputClassPath = jarInput.getFile().getAbsolutePath();
                    classPool.insertClassPath(inputClassPath);
                }
            }
            for (TransformInput input : inputs) {
                for (JarInput jarInput : input.getJarInputs()) {
                    transformInput(jarInput, outputProvider, isIncremental);
                }
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    transformInput(directoryInput, outputProvider, isIncremental);
                }
            }
            transformClassFileExecutor.shutdown();
            transformClassFileExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        } catch (Exception e) {
            println(e);
        }
    }

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

    private void transformInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException, NotFoundException {
        File dest = outputProvider.getContentLocation(
                directoryInput.getName(),
                directoryInput.getContentTypes(),
                directoryInput.getScopes(),
                Format.DIRECTORY);
        FileUtils.forceMkdir(dest);
        File directoryInputFile = directoryInput.getFile();
        final String inputClassPath = directoryInputFile.getAbsolutePath();
        final String destDirPath = dest.getAbsolutePath();
        classPool.insertClassPath(inputClassPath);
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


    private void transformFileInner(String inputClassPath, File dirFile, File inputFile, File outputFile) {
        if (tryTransformDirClassFile(dirFile, inputFile)) {
            try {
                CtClass ctClass = getCtClass(inputClassPath, inputFile);
                println("transformFileInner:" + ctClass.getName());
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


    private void transformJarInner(File inputJarFile, File outputJarFile) {
        try {
            ZipFile inputZip = new ZipFile(inputJarFile);
            ZipOutputStream outputZip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputJarFile.toPath())));
            Enumeration inEntries = inputZip.entries();
            while (inEntries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) inEntries.nextElement();
                InputStream originalFile = new BufferedInputStream(inputZip.getInputStream(entry));
                ZipEntry outEntry = new ZipEntry(entry.getName());
                byte[] newEntryContent;
                if (tryTransformJarClassFile(inputJarFile, outEntry.getName())) {
                    CtClass ctClass = classPool.makeClass(originalFile);
                    println("transformJarInner:" + ctClass.getName());
                    transformJarClassFile(ctClass);
                    newEntryContent = ctClass.toBytecode();
                    ctClass.detach();
                } else {
                    newEntryContent = IOUtils.toByteArray(originalFile);
                }
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
            outputZip.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


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


    private CtClass getCtClass(String className) {
        try {
            return classPool.getCtClass(className);
        } catch (NotFoundException e) {
            println(e);
        }
        return null;
    }


}
