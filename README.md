修改java字节码

题目出自鸿神[玩安卓svip交流群](https://t.zsxq.com/QFuFaIu)

[第一周：尝试修改java字节码](https://t.zsxq.com/jybuB6y)
> 1.选择javassist或者asm尝试修改一个java class，做任意修改即可[达标]。
> 2.尝试给一个java class方法中的方法添加耗时。
> 3.尝试在Android项目编译阶段给java class方法添加耗时检测。

### 1.javassist修改字节码
#### 与javassist的第一次见面
之前在项目开发中为了实现消息推送的各个平台版本sdk（小米，华为，OPPO，vivo，极光）。在写这个多平台推送的sdk过程中，发现小米手机启动时，小米推送和极光推送的服务都同时启动了。导致后台发起的推送收到了两次（后天是全平台推送的）。本来只要手机端只要启动一个推送服务，结果应该只会收到一个推送。当时猜测可能是注册了某个广播接收者然后在某些时候启动了极光服务，现在重新回顾通过Android Studio的`Analyze APK`（build->Analyze APK）时，极光服务是通过`provider`启动的，会有一些sdk会在`provider`中初始化，见[你的Android库是否还在Application中初始化？](https://juejin.im/post/5da6ce99f265da5ba532b6a8)。
``` xml
<provider
        android:name="cn.jpush.android.service.DownloadProvider"
        android:exported="true"
        android:authorities="com.wantu.kouzidashen.DownloadProvider" />
```
``` java
public class DownloadProvider extends ContentProvider {
...
    private void init() {
        try {
            if (a.d(this.getContext().getApplicationContext())) {
                JCoreInterface.register(this.getContext());
            }

        } catch (Throwable var1) {
        }
    }
}

public class JCoreInterface {
	...
	public static void register(Context var0) {
        Bundle var1 = new Bundle();
        i.a().b(var0, "intent.INIT", var1);
    }
}

public final class i {
    public final void b(Context var1, String var2, Bundle var3) {
	    try {
	        var1 = cn.jiguang.d.a.a(var1);
	        if (this.a(var1)) {
	            JCoreInterface.execute("SDK_MAIN", new j(this, var1, var2, var3), new int[0]);
	        }
	    } catch (Throwable var4) {
	        cn.jiguang.e.c.c("JServiceCommandHelper", "onAction failed", var4);
	    }
    }
}
```
在平时启动极光服务通过`JPushInterface.init()`方法最终也会调用`JCoreInterface.execute`。因此为了避免在小米/华为等本身具有推送平台的手机在启动时启动了极光推送，需要设置一个`flag`标志控制`execute`方法的执行：
``` java
public class JCoreInterface{
	public static void execute{
		if(flag)return;//修改的代码
		...
	}
}
```
在开始想通过`JD-GUI`来修改代码，然后编译成新的jar包。但是发现太难了，相关的`Context`环境没有，而且极光的jar包是混淆过的，`JD-GUI`反编译的最终效果不一定每个都正确，会有一些文件不识别。  
事实上我们想要的效果只是修改个别文件，然后覆盖相应的目录即可，这样改动最小。最终通过查询，[javassist](http://www.javassist.org/)(Java Programming Assistant)进入我的视野。  
`javassist`是一个java字节码编辑工具，可以很简单的修改class，操作方式优点类似于反射接口调用。
#### 修改极光jar包，控制服务启动
首先准备`JD-GUI`，`idea`，然后下载[javassist](https://github.com/jboss-javassist/javassist/releases)，在`Android SDK`目录下的`platforms/android-28`下找出`android.jar`，然后下载[极光的jar包](http://docs.jiguang.cn/jpush/resources/)，
我们用`idea`新建一个`java`项目，然后新建`libs`目录，然后加入`javassist.jar`。右键`Add as Library`加入到库中。在src中新建一个`Test`类,
首先在`JCoreInterface`（在`jpush-android-3.2.0.jar`中）中加入`JPUSH_IS_INIT`静态变量。
``` java
public class Test {
    public static void main(String[] args) {
        ClassPool pool = ClassPool.getDefault();
        try {
            pool.insertClassPath("/xxx/JavassistTest/libs/jcore-android-1.2.7.jar");
            pool.insertClassPath("/xxx/JavassistTest/libs/jpush-android-3.2.0.jar");
            pool.insertClassPath("/xxx/JavassistTest/libs/android.jar");
            CtClass c = pool.get("cn.jpush.android.api.JPushInterface");//找到JPushInterface类
            CtField bField = new CtField(CtClass.booleanType,"JPUSH_IS_INIT",c2);//添加JPUSH_IS_INIT静态变量
            bField.setModifiers(Modifier.PUBLIC|Modifier.STATIC);
            c.addField(bField);
            CtMethod initMethod = c.getDeclaredMethod("init");//在init方法最前面插入代码 JPUSH_IS_INIT = true;
            initMethod.insertBefore("JPUSH_IS_INIT = true;");

            CtMethod stopMethod = c.getDeclaredMethod("stopPush");////stopPush方法中 JPUSH_IS_INIT = true;
            stopMethod.insertBefore("JPUSH_IS_INIT = false;");
            c.writeFile("jpush-android"); //输出目录jpush-android
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
在当前工程`jpush-android`目录下，我们找到了`cn/jpush/android/api/JPushInterface.class`,idea打开反编译如下：
``` java
public class JPushInterface {
    ...
    public static boolean JPUSH_IS_INIT;
    ...

    public static void init(Context var0) {
        JPUSH_IS_INIT = true;
        ...
    }

   ...

    public static void stopPush(Context var0) {
        JPUSH_IS_INIT = false;
        g.a("JPushInterface", "action:stopPush");
        ...
    }
```
至此`JPushInterface`已经加入了`JPUSH_IS_INIT`标志，并且在`init`和`stopPush`中进行修改。接着需要修改`jcore-android-1.2.7.jar`中`JCoreInterface.execute`方法。在此之前，需要将当前的`JPushInterface.class`覆盖到`jpush-android-3.2.0.jar`中。拷贝一份命名为`jpush-android-3.2.0-fix.jar`，通过`360压缩`软件打开，将修改`JPushInterface.class`文件覆盖到对应目录即可。接着就可以修改`JCoreInterface`了，代码如下:
``` java
ClassPool pool = ClassPool.getDefault();
try {
    pool.insertClassPath("/xxx/JavassistTest/libs/jcore-android-1.2.7.jar");
    pool.insertClassPath("/xxx/JavassistTest/libs/jpush-android-3.2.0-fix.jar");
    pool.insertClassPath("/xxx/JavassistTest/libs/android.jar");
    CtClass c = pool.get("cn.jiguang.api.JCoreInterface");
    CtMethod method = c.getDeclaredMethod("execute");
    method.insertBefore("if(!cn.jpush.android.api.JPushInterface.JPUSH_IS_INIT)return;");//JPUSH_IS_INIT为false，直接return返回
    c.writeFile("jpush-android");
} catch (Exception e) {
    e.printStackTrace();
}
```
最后得到修改后的`JCoreInterface`,反编译如下：
``` java
package cn.jiguang.api;
...
public class JCoreInterface {
 	public static void execute(String var0, Runnable var1, int... var2) {
        if (JPushInterface.JPUSH_IS_INIT) {
            cn.jiguang.d.h.i.a(var0, var1);
        }
    }
}
```
稍微与修改时候的代码有所不同，但是整体的逻辑是正确的。然后同样通过压缩软件覆盖修改，我们就实现了可控制启动的极光推送jar包。

### 2. ASM添加方法耗时检测
[ASM](https://asm.ow2.io/)是一款字节码操作与分析的开源框架，可以通过二进制形式（内存）修改已有class或者动态生成class。它提供了许多api用于字节码转换构建与分析。较于`javassist`，`ASM`相对复杂，门槛较高。ASM操作基于指令级别，提供了多种修改和分析API，小而快速，强大。
官方的入门教程见[asm4-guide.pdf](https://asm.ow2.io/asm4-guide.pdf)  
[doc文档](https://asm.ow2.io/javadoc/)
由于ASM操作字节码是基于指令的，因此要对`jvm`要有一定了解，推荐大家阅读[《深入理解Java虚拟机》](https://book.douban.com/subject/24722612/)和[《自己动手写Java虚拟机》](https://book.douban.com/subject/26802084/)，而`《自己动手写Java虚拟机》`实践性强，大家可以通过go语言编程的形式学习Java虚拟机。
`ASM`api主要有以下关键类：
[ClassReader](https://asm.ow2.io/javadoc/org/objectweb/asm/ClassReader.html): 用于解析class文件，通过`accept`接收`ClassVisitor`对象访问具体的字段，方法等
[ClassVisitor](https://asm.ow2.io/javadoc/org/objectweb/asm/ClassVisitor.html)：class访问者
[ClassWriter](https://asm.ow2.io/javadoc/org/objectweb/asm/ClassWriter.html): 继承自`ClassVisitor`，用于修改或生成class，通常配合`ClassReader`和`ClassVisitor`修改class
这里在`asm4-guide`第63页通过`LocalVariablesSorter`为方法添加耗时检测
``` java
public class MethodLogAdapter extends ClassVisitor {
    public MethodLogAdapter(int api) {
        super(api);
    }

    private String owner;
    private boolean isInterface;
    public boolean changed; //是否修改过

    public MethodLogAdapter(ClassVisitor cv) {
        super(ASM4, cv);
    }

    @Override
    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces) {
        cv.visit(version, access, name, signature, superName, interfaces);
        owner = name;
        isInterface = (access & ACC_INTERFACE) != 0;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name,
                                     String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature,
                exceptions);
        if (!isInterface && mv != null && !name.equals("<init>")) {
            mv = new MethodLogAdapter.LogMethodAdapter(access, name, desc, mv);
        }
        return mv;
    }


    class LogMethodAdapter extends LocalVariablesSorter {
        private int time;
        private String name;
        private boolean hasMethodLog;//是否具有MethodLog注解

        public LogMethodAdapter(int access, String name, String desc,
                                MethodVisitor mv) {
            super(ASM4, access, desc, mv);
            this.name = name;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (hasMethodLog) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System",
                        "nanoTime", "()J");
                time = newLocal(Type.LONG_TYPE);//声明临时变量time
                mv.visitVarInsn(LSTORE, time);//将返回的时间戳保存到临时变量
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            hasMethodLog = "Lannotations/MethodLog;".equals(descriptor);
            if (!changed && hasMethodLog) changed = true;
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitInsn(int opcode) {
            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
                if (hasMethodLog) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/System",
                            "nanoTime", "()J");
                    mv.visitVarInsn(LLOAD, time);//加载time临时变量
                    mv.visitInsn(LSUB);//与当前时间戳相减
                    mv.visitVarInsn(LSTORE, 3);
                    Label l3 = new Label();
                    mv.visitLabel(l3);
                    //以下是将方法耗时打印出来 Log.i("当前类名","方法名:"+time)
                    mv.visitLdcInsn(owner);
                    mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                    mv.visitLdcInsn(name + ":");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    mv.visitVarInsn(LLOAD, 3);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKESTATIC, "android/util/Log", "i", "(Ljava/lang/String;Ljava/lang/String;)V", false);
                }
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack + 4, maxLocals);
        }
    }
}

```
然后配合`ClassReader`，`ClassWriter`修改class，给`TestActivity`添加方法耗时检测
``` java
public class AsmTest {
    public static void main(String[] args) {
        try {
            changeTest();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //class文件信息读取
    private static void changeTest() throws Exception {
        String classPath = "out/production/ClassEditTest/test/TestActivity.class";
        ClassReader reader = new ClassReader(new FileInputStream(new File(classPath)));
        ClassWriter cw = new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        MethodLogAdapter adapter = new MethodLogAdapter(cw);
        reader.accept(adapter,ClassReader.EXPAND_FRAMES);
        System.out.println(adapter.changed);
        byte[] bytes = cw.toByteArray();
        FileOutputStream fos = new FileOutputStream(new File("test.class"));
        fos.write(bytes);

    }
}
```
对比原先和修改后的代码如下
``` java
public class TestActivity {//修改前

    @MethodLog
    public void test() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void test2() {
       Log.i("test","test123");
    }
}
```
``` java
public class TestActivity {//修改后
    public TestActivity() {
    }

    @MethodLog
    public void test() {
        long var1 = System.nanoTime();

        try {
            Thread.sleep(100L);
        } catch (InterruptedException var5) {
            var5.printStackTrace();
        }

        long var3 = System.nanoTime() - var1;
        Log.i("test/TestActivity", "test:" + var3);
    }

    public void test2() {
        this.test();
    }
}
```

### 3. 使用Transform在Android编译阶段添加方法耗时
之前通过[Transform](http://tools.android.com/tech-docs/new-build-system/transform-api)实现了[简易版路由框架](https://juejin.im/post/5cf35bde6fb9a07ed440e99a)，不过是通过`javassist`实现的，虽然实现更简单，但是不如`ASM`操作快速，所以本次通过`ASM`实现。
同样的创建一个名称为`buildSrc`（注意大小写）的`Android Library`，这样我们的插件直接可以使用了，具体如何实现插件可以参照[基于Transform实现更高效的组件化路由框架](https://juejin.im/post/5cf35bde6fb9a07ed440e99a)的配置方式。
添加`MethodLogTransform`处理方法耗时
``` groovy
class MethodLogTransform extends Transform {
    @Override
    String getName() {
        return "MethodLog"
    }

	...
    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        for (TransformInput input : inputs) {
            for (DirectoryInput dirInput : input.directoryInputs) {//目录中的class文件
                readClassWithPath(dirInput.file)
                File dest = outputProvider.getContentLocation(dirInput.name,
                        dirInput.contentTypes,
                        dirInput.scopes,
                        Format.DIRECTORY)
                FileUtils.copyDirectory(dirInput.file, dest)
            }
            for (JarInput jarInput : input.jarInputs) {//jar（第三方库，module）
                if (jarInput.scopes.contains(QualifiedContent.Scope.SUB_PROJECTS)) {//module library
					//todo 为jar包添加耗时
                }
                copyFile(jarInput, outputProvider)
            }
        }
    }
    //
    void readClassWithPath(File dir) {//从编译class文件目录找到注解
        def root = dir.absolutePath
        dir.eachFileRecurse { File file ->
            def filePath = file.absolutePath
            if (!filePath.endsWith(".class")) return
            def className = getClassName(root, filePath)
            if (isSystemClass(className)) return
            hookClass(filePath, className)
        }
    }

    void hookClass(String filePath, String className) {
        ClassReader reader = new ClassReader(new FileInputStream(new File(filePath)))
        ClassWriter cw = new ClassWriter(reader,ClassWriter.COMPUTE_MAXS)
        MethodLogAdapter adapter = new MethodLogAdapter(cw)
        reader.accept(adapter,ClassReader.EXPAND_FRAMES)
        System.out.println(adapter.changed)
        if(adapter.changed){
            byte[] bytes = cw.toByteArray()
            FileOutputStream fos = new FileOutputStream(new File(filePath))
            fos.write(bytes)
        }

    }

    ...
}
```
### 项目地址























