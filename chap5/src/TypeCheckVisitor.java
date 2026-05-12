import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import syntaxtree.*;
import visitor.*;

public class TypeCheckVisitor implements TypeVisitor {
    private static class TooManyErrorsException extends RuntimeException {}
    private enum Phase { COLLECT, CHECK }

    private static class MethodInfo {
        Type returnType;
        LinkedHashMap<String, Type> params = new LinkedHashMap<>();
        HashMap<String, Type> locals = new HashMap<>();
    }

    private static class ClassInfo {
        String name;
        String parent; // may be null
        HashMap<String, Type> fields = new HashMap<>();
        HashMap<String, MethodInfo> methods = new HashMap<>();
    }

    private final HashMap<String, ClassInfo> classes = new HashMap<>();
    private ClassInfo currentClass = null;
    private MethodInfo currentMethod = null;
    private Phase phase = Phase.COLLECT;
    private final List<String> errors = new ArrayList<>();
    private final Integer MAX_ERRORS = 3;

    public boolean check(Program program) {
        try {
            phase = Phase.COLLECT; // 1단계 설정. 근데, phase 초기화를 COLLECT로 해두니 굳이 안 해도 될 듯
            /*
            this => TypeCheckVisitor
            TypeCheckVisitor를 인자로 program의 accept() 호출 ->
            program.accept()에선 TypeCheckVisitor.visit(Program)을 호출하여 Program에 대한 Type Check을 시작함.
            */
            program.accept(this);

            phase = Phase.CHECK;
            program.accept(this);
        } catch (TooManyErrorsException e) {
            return printErrors();
        }

        return printErrors();
    }

    /*
    PrintErrors()가 호출되는 시점
    1. 허용된 에러 개수를 초과한 경우
    2. Type Checking 2단계까지 다 정상적으로 끝난 이후
    */
    public boolean printErrors() { // boolean -> void
        if (errors.isEmpty()) return true;

        else {
            for (String error : errors)
                System.err.println(error);
            return false;
        }
    }

    // 에러를 저장하는 errors 리스트에 에러 메시지(message)를 add하는 메서드
    private void report(String message) {
        if (errors.size() < MAX_ERRORS)
            errors.add(message);

        else if (errors.size() >= MAX_ERRORS)
            throw new TooManyErrorsException();
    }

    private String typeToString(Type type) {
        if (type == null) {
            return "<unknown>";
        }
        if (type instanceof IntegerType) {
            return "int";
        }
        if (type instanceof BooleanType) {
            return "boolean";
        }
        if (type instanceof IntArrayType) {
            return "int[]";
        }
        if (type instanceof IdentifierType) {
            return ((IdentifierType) type).s;
        }
        return type.getClass().getSimpleName();
    }

    private boolean sameType(Type left, Type right) {
        if (left == null || right == null) return false;

        if (left.getClass() == right.getClass()) {
            if (left instanceof IdentifierType) {
                String leftString = ((IdentifierType) left).s;
                String rightString = ((IdentifierType) right).s;
                return leftString.equals(rightString);
            }
            return true;
        }

        return false;
    }

    private boolean isSubtype(Type sub, Type sup) {
        if (sub == null || sup == null) {
            return false;
        }
        if (sameType(sub, sup)) {
            return true;
        }
        if (!(sub instanceof IdentifierType) || !(sup instanceof IdentifierType)) {
            return false;
        }

        String child = ((IdentifierType) sub).s;
        String target = ((IdentifierType) sup).s;

        while (true) {
            ClassInfo ci = classes.get(child);
            if (ci == null || ci.parent == null) {
                return false;
            }
            if (ci.parent.equals(target)) {
                return true;
            }
            child = ci.parent;
        }
    }

    private boolean isKnownType(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof IntegerType || type instanceof BooleanType || type instanceof IntArrayType) {
            return true;
        }
        if (type instanceof IdentifierType) {
            return classes.containsKey(((IdentifierType) type).s);
        }
        return false;
    }

    private Type lookupVar(String name) {
        if (currentMethod != null) {
            Type local = currentMethod.locals.get(name);
            if (local != null) {
                return local;
            }
            Type param = currentMethod.params.get(name);
            if (param != null) {
                return param;
            }
        }

        ClassInfo ci = currentClass;
        while (ci != null) {
            Type field = ci.fields.get(name);
            if (field != null) {
                return field;
            }
            ci = ci.parent == null ? null : classes.get(ci.parent);
        }
        return null;
    }

    private MethodInfo lookupMethod(String className, String methodName) {
        ClassInfo ci = classes.get(className);
        while (ci != null) {
            MethodInfo method = ci.methods.get(methodName);
            if (method != null) {
                return method;
            }
            ci = ci.parent == null ? null : classes.get(ci.parent);
        }
        return null;
    }

    private void checkDeclaredType(Type type, String subject) {
        if (!isKnownType(type)) {
            report("Unknown type for " + subject + ": " + typeToString(type));
        }
    }

    private void checkBinaryOperands(String op, Type left, Type right, Class<? extends Type> expected) {
        if (!expected.isInstance(left) || !expected.isInstance(right)) {
            report("Type mismatch in " + op + ": expected "
                + expectedTypeName(expected) + " operands, found "
                + typeToString(left) + " and " + typeToString(right));
        }
    }

    private String expectedTypeName(Class<? extends Type> typeClass) {
        if (typeClass == IntegerType.class) {
            return "int";
        }
        if (typeClass == BooleanType.class) {
            return "boolean";
        }
        if (typeClass == IntArrayType.class) {
            return "int[]";
        }
        return typeClass.getSimpleName();
    }

    @Override
    public Type visit(Program n) {
        n.m.accept(this);
        for (int i = 0; i < n.cl.size(); i++) {
            n.cl.elementAt(i).accept(this);
        }
        return null;
    }

    @Override
    public Type visit(MainClass n) {
        if (phase == Phase.COLLECT) {
            String className = n.i1.s;
            if (classes.containsKey(className)) {
                report("Duplicate class declaration: " + className);
            } else {
                ClassInfo ci = new ClassInfo();
                ci.name = className;
                classes.put(className, ci);
            }
            return null;
        }

        currentClass = classes.get(n.i1.s);
        currentMethod = null;
        if (currentClass == null) {
            report("Main class not found in symbol table: " + n.i1.s);
            return null;
        }
        n.s.accept(this);
        currentClass = null;
        return null;
    }

    @Override
    public Type visit(ClassDeclSimple n) {
        if (phase == Phase.COLLECT) {
            String className = n.i.s;
            if (classes.containsKey(className)) {
                report("Duplicate class declaration: " + className);
                currentClass = classes.get(className);
            } else {
                ClassInfo ci = new ClassInfo();
                ci.name = className;
                classes.put(className, ci);
                currentClass = ci;
            }

            for (int i = 0; i < n.vl.size(); i++) {
                n.vl.elementAt(i).accept(this);
            }
            for (int i = 0; i < n.ml.size(); i++) {
                n.ml.elementAt(i).accept(this);
            }
            currentClass = null;
            return null;
        }

        currentClass = classes.get(n.i.s);
        if (currentClass == null) {
            report("Undeclared class in check phase: " + n.i.s);
            return null;
        }

        for (int i = 0; i < n.vl.size(); i++) {
            n.vl.elementAt(i).accept(this);
        }
        for (int i = 0; i < n.ml.size(); i++) {
            n.ml.elementAt(i).accept(this);
        }
        currentClass = null;
        return null;
    }

    @Override
    public Type visit(ClassDeclExtends n) {
        if (phase == Phase.COLLECT) {
            String className = n.i.s;
            if (classes.containsKey(className)) {
                report("Duplicate class declaration: " + className);
                currentClass = classes.get(className);
            } else {
                ClassInfo ci = new ClassInfo();
                ci.name = className;
                ci.parent = n.j.s;
                classes.put(className, ci);
                currentClass = ci;
            }

            for (int i = 0; i < n.vl.size(); i++) {
                n.vl.elementAt(i).accept(this);
            }
            for (int i = 0; i < n.ml.size(); i++) {
                n.ml.elementAt(i).accept(this);
            }
            currentClass = null;
            return null;
        }

        currentClass = classes.get(n.i.s);
        if (currentClass == null) {
            report("Undeclared class in check phase: " + n.i.s);
            return null;
        }
        if (!classes.containsKey(n.j.s)) {
            report("Undeclared parent class " + n.j.s + " for class " + n.i.s);
        }

        for (int i = 0; i < n.vl.size(); i++) {
            n.vl.elementAt(i).accept(this);
        }
        for (int i = 0; i < n.ml.size(); i++) {
            n.ml.elementAt(i).accept(this);
        }
        currentClass = null;
        return null;
    }

    @Override
    public Type visit(VarDecl n) {
        if (phase == Phase.COLLECT) {
            if (currentClass == null) {
                return null;
            }
            if (currentMethod == null) {
                if (currentClass.fields.containsKey(n.i.s)) {
                    report("Duplicate field declaration: " + n.i.s + " in class " + currentClass.name);
                } else {
                    currentClass.fields.put(n.i.s, n.t);
                }
            } else {
                if (currentMethod.locals.containsKey(n.i.s) || currentMethod.params.containsKey(n.i.s)) {
                    report("Duplicate local declaration: " + n.i.s + " in method of class " + currentClass.name);
                } else {
                    currentMethod.locals.put(n.i.s, n.t);
                }
            }
            return null;
        }

        checkDeclaredType(n.t, "variable " + n.i.s);
        return null;
    }

    @Override
    public Type visit(MethodDecl n) {
        if (phase == Phase.COLLECT) {
            if (currentClass == null) {
                return null;
            }
            if (currentClass.methods.containsKey(n.i.s)) {
                report("Duplicate method declaration: " + n.i.s + " in class " + currentClass.name);
                currentMethod = currentClass.methods.get(n.i.s);
            } else {
                MethodInfo method = new MethodInfo();
                method.returnType = n.t;
                currentClass.methods.put(n.i.s, method);
                currentMethod = method;
            }

            for (int i = 0; i < n.fl.size(); i++) {
                n.fl.elementAt(i).accept(this);
            }
            for (int i = 0; i < n.vl.size(); i++) {
                n.vl.elementAt(i).accept(this);
            }
            currentMethod = null;
            return null;
        }

        currentMethod = currentClass == null ? null : currentClass.methods.get(n.i.s);
        if (currentMethod == null) {
            report("Undeclared method in check phase: " + n.i.s);
            return null;
        }

        checkDeclaredType(currentMethod.returnType, "return type of method " + n.i.s);
        for (int i = 0; i < n.fl.size(); i++) {
            n.fl.elementAt(i).accept(this);
        }
        for (int i = 0; i < n.vl.size(); i++) {
            n.vl.elementAt(i).accept(this);
        }
        for (int i = 0; i < n.sl.size(); i++) {
            n.sl.elementAt(i).accept(this);
        }

        Type actualReturn = n.e.accept(this);
        if (!sameType(actualReturn, currentMethod.returnType)
            && !isSubtype(actualReturn, currentMethod.returnType)) {
            report("Return type mismatch in method " + n.i.s + ": expected "
                + typeToString(currentMethod.returnType) + ", found " + typeToString(actualReturn));
        }

        currentMethod = null;
        return null;
    }

    @Override
    public Type visit(Formal n) {
        if (phase == Phase.COLLECT) {
            if (currentMethod == null) {
                return null;
            }
            if (currentMethod.params.containsKey(n.i.s)) {
                report("Duplicate parameter declaration: " + n.i.s);
            } else {
                currentMethod.params.put(n.i.s, n.t);
            }
            return null;
        }

        checkDeclaredType(n.t, "parameter " + n.i.s);
        return null;
    }

    @Override
    public Type visit(IntArrayType n) {
        return n;
    }

    @Override
    public Type visit(BooleanType n) {
        return n;
    }

    @Override
    public Type visit(IntegerType n) {
        return n;
    }

    @Override
    public Type visit(IdentifierType n) {
        if (phase == Phase.CHECK && !classes.containsKey(n.s)) {
            report("Undeclared class type: " + n.s);
        }
        return n;
    }

    @Override
    public Type visit(Block n) {
        for (int i = 0; i < n.sl.size(); i++) {
            n.sl.elementAt(i).accept(this);
        }
        return null;
    }

    @Override
    public Type visit(If n) {
        Type cond = n.e.accept(this);
        if (!(cond instanceof BooleanType)) {
            report("Condition of if must be boolean, found " + typeToString(cond));
        }
        n.s1.accept(this);
        n.s2.accept(this);
        return null;
    }

    @Override
    public Type visit(While n) {
        Type cond = n.e.accept(this);
        if (!(cond instanceof BooleanType)) {
            report("Condition of while must be boolean, found " + typeToString(cond));
        }
        n.s.accept(this);
        return null;
    }

    @Override
    public Type visit(Print n) {
        Type expr = n.e.accept(this);
        if (!(expr instanceof IntegerType)) {
            report("System.out.println expects int, found " + typeToString(expr));
        }
        return null;
    }

    @Override
    public Type visit(Assign n) {
        Type lhs = lookupVar(n.i.s);
        if (lhs == null) {
            report("Undeclared identifier in assignment: " + n.i.s);
            n.e.accept(this);
            return null;
        }

        Type rhs = n.e.accept(this);
        if (!sameType(rhs, lhs) && !isSubtype(rhs, lhs)) {
            report("Type mismatch in assignment to " + n.i.s + ": expected "
                + typeToString(lhs) + ", found " + typeToString(rhs));
        }
        return null;
    }

    @Override
    public Type visit(ArrayAssign n) {
        Type arrayType = lookupVar(n.i.s);
        if (!(arrayType instanceof IntArrayType)) {
            report("Array assignment requires int[] variable " + n.i.s + ", found " + typeToString(arrayType));
        }

        Type indexType = n.e1.accept(this);
        if (!(indexType instanceof IntegerType)) {
            report("Array index must be int, found " + typeToString(indexType));
        }

        Type valueType = n.e2.accept(this);
        if (!(valueType instanceof IntegerType)) {
            report("Array assignment value must be int, found " + typeToString(valueType));
        }
        return null;
    }

    @Override
    public Type visit(And n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands("&&", left, right, BooleanType.class);
        return new BooleanType();
    }

    @Override
    public Type visit(LessThan n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands("<", left, right, IntegerType.class);
        return new BooleanType();
    }

    @Override
    public Type visit(Plus n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands("+", left, right, IntegerType.class);
        return new IntegerType();
    }

    @Override
    public Type visit(Minus n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands("-", left, right, IntegerType.class);
        return new IntegerType();
    }

    @Override
    public Type visit(Times n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands("*", left, right, IntegerType.class);
        return new IntegerType();
    }

    @Override
    public Type visit(ArrayLookup n) {
        Type arrayType = n.e1.accept(this);
        Type indexType = n.e2.accept(this);

        if (!(arrayType instanceof IntArrayType)) {
            report("Array lookup requires int[] receiver, found " + typeToString(arrayType));
        }
        if (!(indexType instanceof IntegerType)) {
            report("Array lookup index must be int, found " + typeToString(indexType));
        }
        return new IntegerType();
    }

    @Override
    public Type visit(ArrayLength n) {
        Type arrayType = n.e.accept(this);
        if (!(arrayType instanceof IntArrayType)) {
            report("length requires int[] receiver, found " + typeToString(arrayType));
        }
        return new IntegerType();
    }

    @Override
    public Type visit(Call n) {
        Type receiver = n.e.accept(this);
        if (!(receiver instanceof IdentifierType)) {
            report("Method call receiver must be an object, found " + typeToString(receiver));
            for (int i = 0; i < n.el.size(); i++) {
                n.el.elementAt(i).accept(this);
            }
            return null;
        }

        String className = ((IdentifierType) receiver).s;
        MethodInfo method = lookupMethod(className, n.i.s);
        if (method == null) {
            report("Unknown method " + n.i.s + " for class " + className);
            for (int i = 0; i < n.el.size(); i++) {
                n.el.elementAt(i).accept(this);
            }
            return null;
        }

        if (n.el.size() != method.params.size()) {
            report("Method call argument count mismatch for " + n.i.s + ": expected "
                + method.params.size() + ", found " + n.el.size());
        }

        int argCount = Math.min(n.el.size(), method.params.size());
        List<Type> paramTypes = new ArrayList<>(method.params.values());
        for (int i = 0; i < argCount; i++) {
            Type actual = n.el.elementAt(i).accept(this);
            Type expected = paramTypes.get(i);
            if (!sameType(actual, expected) && !isSubtype(actual, expected)) {
                report("Method call argument type mismatch for " + n.i.s + " at position " + (i + 1)
                    + ": expected " + typeToString(expected) + ", found " + typeToString(actual));
            }
        }
        for (int i = argCount; i < n.el.size(); i++) {
            n.el.elementAt(i).accept(this);
        }

        return method.returnType;
    }

    @Override
    public Type visit(IntegerLiteral n) {
        return new IntegerType();
    }

    @Override
    public Type visit(True n) {
        return new BooleanType();
    }

    @Override
    public Type visit(False n) {
        return new BooleanType();
    }

    @Override
    public Type visit(IdentifierExp n) {
        Type found = lookupVar(n.s);
        if (found == null) {
            report("Undeclared identifier: " + n.s);
        }
        return found;
    }

    @Override
    public Type visit(This n) {
        if (currentClass == null) {
            report("this used outside of class context");
            return null;
        }
        return new IdentifierType(currentClass.name);
    }

    @Override
    public Type visit(NewArray n) {
        Type sizeType = n.e.accept(this);
        if (!(sizeType instanceof IntegerType)) {
            report("Array size expression must be int, found " + typeToString(sizeType));
        }
        return new IntArrayType();
    }

    @Override
    public Type visit(NewObject n) {
        if (!classes.containsKey(n.i.s)) {
            report("Instantiation of undeclared class: " + n.i.s);
        }
        return new IdentifierType(n.i.s);
    }

    @Override
    public Type visit(Not n) {
        Type expr = n.e.accept(this);
        if (!(expr instanceof BooleanType)) {
            report("! expects boolean, found " + typeToString(expr));
        }
        return new BooleanType();
    }

    @Override
    public Type visit(Identifier n) {
        Type found = lookupVar(n.s);
        if (found == null) {
            report("Undeclared identifier: " + n.s);
        }
        return found;
    }
}
