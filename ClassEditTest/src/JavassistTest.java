import javassist.*;

public class JavassistTest {
    public static void main(String[] args) {
        try {
            changeJPush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            changeJCore();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void changeJCore()throws Exception{
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath("libs/jcore-android-1.2.7.jar");
        pool.insertClassPath("libs/jpush-android-3.2.0-fix.jar");
        pool.insertClassPath("libs/android.jar");
        CtClass c1 = pool.get("cn.jiguang.api.JCoreInterface");
        CtMethod method = c1.getDeclaredMethod("execute");
        method.insertBefore("if(!cn.jpush.android.api.JPushInterface.JPUSH_IS_INIT)return;");
        c1.writeFile("jcore");
    }

    private static void changeJPush() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath("libs/jcore-android-1.2.7.jar");
        pool.insertClassPath("libs/jpush-android-3.2.0.jar");
        pool.insertClassPath("libs/android.jar");
        CtClass c = pool.get("cn.jpush.android.api.JPushInterface");
        CtField bField = new CtField(CtClass.booleanType, "JPUSH_IS_INIT", c);
        bField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        c.addField(bField);
        CtMethod initMethod = c.getDeclaredMethod("init");
        initMethod.insertBefore("JPUSH_IS_INIT = true;");

        CtMethod stopMethod = c.getDeclaredMethod("stopPush");
        stopMethod.insertBefore("JPUSH_IS_INIT = false;");
        c.writeFile("jpush");
    }
}
