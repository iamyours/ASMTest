import adapter.MethodLogAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;

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
