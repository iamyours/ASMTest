package adapter;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import static jdk.internal.org.objectweb.asm.Opcodes.ASM4;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

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
            MethodLogAdapter.LogMethodAdapter adapter = new MethodLogAdapter.LogMethodAdapter(access, name, desc, mv);
            mv = adapter;
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
