import java.util.*;

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
    private final Integer MAX_ERRORS = 5;

    private final Set<MethodDecl> invalidMethods = new HashSet<>();
    private final Set<MainClass> invalidMainClasses = new HashSet<>();
    private final Set<ClassDecl> invalidClasses = new HashSet<>();

    public boolean check(Program program) {
        try {
            program.accept(this);

            String cyclicClass = acyclic();
            if (cyclicClass != null) {
                report("Cyclic inheritance detected involving class " + cyclicClass);
                return printErrors();
            }

            phase = Phase.CHECK;
            program.accept(this);
        }
        catch (TooManyErrorsException e) {
            return printErrors();
        }

        return printErrors();
    }

    /*
    PrintErrors()가 호출되는 시점
    1. 허용된 에러 개수를 초과한 경우
    2. Type Checking 2단계까지 다 정상적으로 끝난 이후
    */
    public boolean printErrors() {
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

    private void report(ASTNode node, String message) {
        if (node != null && node.line >= 0 && node.column >= 0) // Node의 위치 정보가 없다면 line, column이 -1로 초기화 됨 (소스코드 연산 표현용 IntegerType 등)
            report(message + " at line " + node.line + ", column " + node.column);

        else report(message);
    }

    private String typeToString(Type type) {
        if (type == null)
            return "<unknown>";

        if (type instanceof IntegerType)
            return "int";

        if (type instanceof BooleanType)
            return "boolean";

        if (type instanceof IntArrayType)
            return "int[]";

        if (type instanceof IdentifierType)
            return ((IdentifierType) type).s;

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
        if (sub == null || sup == null)
            return false;

        if (sameType(sub, sup))
            return true;

        if (!(sub instanceof IdentifierType) || !(sup instanceof IdentifierType))
            return false;

        String child = ((IdentifierType) sub).s;
        String target = ((IdentifierType) sup).s;

        while (true) {
            ClassInfo ci = classes.get(child);
            if (ci == null || ci.parent == null)
                return false;

            if (ci.parent.equals(target))
                return true;

            child = ci.parent;
        }
    }

    private boolean isKnownType(Type type) {
        if (type == null)
            return false;

        if (type instanceof IntegerType || type instanceof BooleanType || type instanceof IntArrayType)
            return true;

        if (type instanceof IdentifierType)
            return classes.containsKey(((IdentifierType) type).s);

        return false;
    }

    // 어떤 변수 이름이 어떤 타입인지 찾아주는 보조 메서드. x, num_aux같은 식별자 나오면, 선언되어 있는지, 선언돼 있다면 어떤 타입인지 찾음
    private Type lookupVar(String name) {
        if (currentMethod != null) {
            Type local = currentMethod.locals.get(name); // name이 지역변수인 경우
            if (local != null)
                return local;

            Type param = currentMethod.params.get(name); // name이 매개변수인 경우
            if (param != null)
                return param;
        }

        // currentMethod가 null => 지역변수/매개변수 스코프가 없으니 name이 필드인지 확인
        // 지역변수/매개변수에서 name을 못 찾았다면 name이 필드인지 확인
        ClassInfo ci = currentClass;
        while (ci != null) {
            Type field = ci.fields.get(name);
            if (field != null) return field;

            ci = ci.parent == null ? null : classes.get(ci.parent); // 부모class에 있는 field인지 확인
        }

        return null; // 부모 클래스까지 뒤졌을 때 name이 없다면 name은 선언되지 않은 변수
    }

    private MethodInfo methodtype(String className, String methodName) {
        ClassInfo ci = classes.get(className);
        while (ci != null) {
            MethodInfo method = ci.methods.get(methodName);
            if (method != null) return method;

            ci = ci.parent == null ? null : classes.get(ci.parent);
        }
        return null;
    }

    private void checkDeclaredType(Type type, String subject) {
        if (!isKnownType(type)) {
            report(type, "Unknown type for " + subject + ": " + typeToString(type));
        }
    }

    private void checkBinaryOperands(ASTNode node, String op, Type left, Type right, Class<? extends Type> expected) {
        if (!expected.isInstance(left) || !expected.isInstance(right)) {
            report(node, "Type mismatch in " + op + ": expected "
                + expectedTypeName(expected) + " operands, found "
                + typeToString(left) + " and " + typeToString(right));
        }
    }

    private String expectedTypeName(Class<? extends Type> typeClass) {
        if (typeClass == IntegerType.class)
            return "int";

        if (typeClass == BooleanType.class)
            return "boolean";

        if (typeClass == IntArrayType.class)
            return "int[]";

        return typeClass.getSimpleName();
    }

//    private boolean hasSameMethodInParent(ClassInfo ci, String methodName) {
//        if (ci == null) return false;
//
//        String parentName = ci.parent;
//        while (parentName != null) {
//            ClassInfo parentInfo = classes.get(parentName);
//            if (parentInfo == null) return false;
//
//            if (parentInfo.methods.containsKey(methodName)) return true;
//
//            parentName = parentInfo.parent;
//        }
//
//        return false;
//    }

    private String acyclic() {
        for (String className : classes.keySet()) {
            Set<String> seen = new HashSet<>();
            String current = className;

            while (current != null) {
                if (seen.contains(current))
                    return current;

                seen.add(current);

                ClassInfo ci = classes.get(current);
                if (ci == null) break;

                current = ci.parent;
            }
        }

        return null;
    }

    @Override
    public Type visit(Program n) {
        n.m.accept(this);
        for (int i = 0; i < n.cl.size(); i++)
            n.cl.elementAt(i).accept(this);

        return null;
    }

    @Override
    public Type visit(MainClass n) {
        if (phase == Phase.COLLECT) {
            String className = n.i1.s;
            if (classes.containsKey(className)) {
                report(n.i1, "Duplicate class declaration: " + className);
                invalidMainClasses.add(n);
            }
            else {
                ClassInfo ci = new ClassInfo();
                ci.name = className;
                classes.put(className, ci);
            }

            return null;
        }

        // 2단계: CHECK
        if (invalidMainClasses.contains(n)) return null;

        currentClass = classes.get(n.i1.s);
        currentMethod = null;
        if (currentClass == null) {
            report(n.i1, "Main class not found in symbol table: " + n.i1.s);
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
                report(n.i, "Duplicate class declaration: " + className);
                invalidClasses.add(n);
                return null;
            }
            else {
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

        // 2단계: CHECK
        if (invalidClasses.contains(n)) return null;

        currentClass = classes.get(n.i.s);
        if (currentClass == null) {
            report(n.i, "Undeclared class in check phase: " + n.i.s);
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
                report(n.i, "Duplicate class declaration: " + className);
                invalidClasses.add(n);
                return null;
            }
            else {
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

        if (invalidClasses.contains(n)) return null;

        currentClass = classes.get(n.i.s);
        if (currentClass == null) {
            report(n.i, "Undeclared class in check phase: " + n.i.s);
            return null;
        }
        if (!classes.containsKey(n.j.s)) {
            report(n.j, "Undeclared parent class " + n.j.s + " for class " + n.i.s);
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
            if (currentClass == null)
                return null;

            if (currentMethod == null) {
                if (currentClass.fields.containsKey(n.i.s))
                    report(n.i, "Duplicate field declaration: " + n.i.s + " in class " + currentClass.name);
                else
                    currentClass.fields.put(n.i.s, n.t);
            }
            else {
                if (currentMethod.locals.containsKey(n.i.s) || currentMethod.params.containsKey(n.i.s))
                    report(n.i, "Duplicate local declaration: " + n.i.s + " in method of class " + currentClass.name);

                else
                    currentMethod.locals.put(n.i.s, n.t);
            }

            return null;
        }

        checkDeclaredType(n.t, "variable " + n.i.s);
        return null;
    }

    @Override
    public Type visit(MethodDecl n) {
        if (phase == Phase.COLLECT) {
            if (currentClass == null)
                return null;

            if (currentClass.methods.containsKey(n.i.s)) {
                report(n.i, "Duplicate method declaration: " + n.i.s + " in class " + currentClass.name);
                invalidMethods.add(n);
                return null;
            }
//            else if (hasSameMethodInParent(currentClass, n.i.s)) {
//                report(n.i, "Method overriding is not allowed: '" + n.i.s + "(..)' in class '" + currentClass.name + "'");
//                invalidMethods.add(n);
//                return null;
//            }
            else {
                MethodInfo method = new MethodInfo();
                method.returnType = n.t;
                currentClass.methods.put(n.i.s, method);
                currentMethod = method;
            }

            for (int i = 0; i < n.fl.size(); i++)
                n.fl.elementAt(i).accept(this);

            for (int i = 0; i < n.vl.size(); i++)
                n.vl.elementAt(i).accept(this);

            currentMethod = null;
            return null;
        }

        // 2단계: CHECK
        if (invalidMethods.contains(n)) return null;

        currentMethod = currentClass == null ? null : currentClass.methods.get(n.i.s);
        if (currentMethod == null) {
            report(n.i, "Undeclared method in check phase: " + n.i.s);
            return null;
        }

        checkDeclaredType(currentMethod.returnType, "return type of method " + n.i.s);
        for (int i = 0; i < n.fl.size(); i++)
            n.fl.elementAt(i).accept(this);

        for (int i = 0; i < n.vl.size(); i++)
            n.vl.elementAt(i).accept(this);

        for (int i = 0; i < n.sl.size(); i++)
            n.sl.elementAt(i).accept(this);


        Type actualReturn = n.e.accept(this);
        if (!sameType(actualReturn, currentMethod.returnType)
            && !isSubtype(actualReturn, currentMethod.returnType)) {
            report(n.e, "Return type mismatch in method " + n.i.s + ": expected "
                + typeToString(currentMethod.returnType) + ", found " + typeToString(actualReturn));
        }

        currentMethod = null;
        return null;
    }

    @Override
    public Type visit(Formal n) {
        if (phase == Phase.COLLECT) {
            if (currentMethod == null)
                return null;

            if (currentMethod.params.containsKey(n.i.s))
                report(n.i, "Duplicate parameter declaration: " + n.i.s);
            else
                currentMethod.params.put(n.i.s, n.t);

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
        if (phase == Phase.CHECK && !classes.containsKey(n.s))
            report(n, "Undeclared class type: " + n.s);

        return n;
    }

    @Override
    public Type visit(Block n) {
        for (int i = 0; i < n.sl.size(); i++)
            n.sl.elementAt(i).accept(this);

        return null;
    }

    @Override
    public Type visit(If n) {
        Type cond = n.e.accept(this);
        if (!(cond instanceof BooleanType))
            report(n.e, "Condition of if must be boolean, but found " + typeToString(cond));

        n.s1.accept(this);
        n.s2.accept(this);
        return null;
    }

    @Override
    public Type visit(While n) {
        Type cond = n.e.accept(this);
        if (!(cond instanceof BooleanType))
            report(n.e, "Condition of while must be boolean, found " + typeToString(cond));

        n.s.accept(this);
        return null;
    }

    @Override
    public Type visit(Print n) {
        Type expr = n.e.accept(this);
        if (!(expr instanceof IntegerType)) // MiniJava 7.6 (31)
            report(n.e, "System.out.println expects int, found " + typeToString(expr));

        return null;
    }

    @Override
    public Type visit(Assign n) {
        Type lhs = lookupVar(n.i.s);
        if (lhs == null) {
            report(n.i, "Undeclared identifier in assignment: " + n.i.s);
            n.e.accept(this);
            return null;
        }

        Type rhs = n.e.accept(this);
        if (!sameType(rhs, lhs) && !isSubtype(rhs, lhs))
            report(n.i, "Type mismatch in assignment to " + n.i.s + ": expected "
                + typeToString(lhs) + ", found " + typeToString(rhs));

        return null;
    }

    @Override
    public Type visit(ArrayAssign n) {
        Type arrayType = lookupVar(n.i.s);
        if (!(arrayType instanceof IntArrayType))
            report(n.i, "Array assignment requires int[] variable " + n.i.s + ", found " + typeToString(arrayType));


        Type indexType = n.e1.accept(this);
        if (!(indexType instanceof IntegerType))
            report(n.e1, "Array index must be int, found " + typeToString(indexType));


        Type valueType = n.e2.accept(this);
        if (!(valueType instanceof IntegerType))
            report(n.e2, "Array assignment value must be int, found " + typeToString(valueType));

        return null;
    }

    @Override
    public Type visit(And n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands(n, "&&", left, right, BooleanType.class);

        return new BooleanType();
    }

    @Override
    public Type visit(LessThan n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands(n, "<", left, right, IntegerType.class);

        return new BooleanType();
    }

    @Override
    public Type visit(Plus n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands(n, "+", left, right, IntegerType.class);

        return new IntegerType();
    }

    @Override
    public Type visit(Minus n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands(n, "-", left, right, IntegerType.class);

        return new IntegerType();
    }

    @Override
    public Type visit(Times n) {
        Type left = n.e1.accept(this);
        Type right = n.e2.accept(this);
        checkBinaryOperands(n, "*", left, right, IntegerType.class);

        return new IntegerType();
    }

    @Override
    public Type visit(ArrayLookup n) { // MiniJava 7.7 (37)
        Type arrayType = n.e1.accept(this);
        Type indexType = n.e2.accept(this);

        if (!(arrayType instanceof IntArrayType))
            report(n.e1, "Array lookup requires int[] receiver, but found " + typeToString(arrayType));

        if (!(indexType instanceof IntegerType))
            report(n.e2, "Array lookup index must be int, but found " + typeToString(indexType));


        return new IntegerType();
    }

    @Override
    public Type visit(ArrayLength n) { // 7.7 (38)
        Type arrayType = n.e.accept(this);

        if (!(arrayType instanceof IntArrayType))
            report(n.e, "length requires int[] receiver, but found " + typeToString(arrayType));

        return new IntegerType();
    }

    @Override
    public Type visit(Call n) {
        Type receiver = n.e.accept(this);

        if (!(receiver instanceof IdentifierType)) {
            report(n.e, "Method call receiver must be an object, but found " + typeToString(receiver));
            for (int i = 0; i < n.el.size(); i++)
                n.el.elementAt(i).accept(this);

            return null;
        }

        String className = ((IdentifierType) receiver).s;
        MethodInfo method = methodtype(className, n.i.s);
        if (method == null) {
            report(n.i, "Unknown method " + n.i.s + " for class " + className);

            for (int i = 0; i < n.el.size(); i++)
                n.el.elementAt(i).accept(this);

            return null;
        }

        if (n.el.size() != method.params.size())
            report(n.i, "Method call argument count mismatch for " + n.i.s + ": expected "
                + method.params.size() + ", found " + n.el.size());

        // 실인자 개수까지만 비교하기 위함.
        // 만약 실인자 개수 > 메서드 매개변수 개수라면,
        // 메서드 매개변수 개수까지만 체크하기 위해 min()
        int argCount = Math.min(n.el.size(), method.params.size());
        List<Type> paramTypes = new ArrayList<>(method.params.values());
        for (int i = 0; i < argCount; i++) {
            Exp arg = n.el.elementAt(i);
            Type actual = n.el.elementAt(i).accept(this); // 실인자 타입
            Type expected = paramTypes.get(i); // 선언된 매개변수 타입
            if (!sameType(actual, expected) && !isSubtype(actual, expected)) // 실인자 타입 <= 매개변수 타입이 아니라면 타입 불일치
                report(arg, "Method call argument type mismatch for " + n.i.s + " at position " + (i + 1)
                    + ": expected " + typeToString(expected) + ", but found " + typeToString(actual));

        }

        // 실인자 개수 > 매개변수 개수인 경우, 나머지 실인자들에 대한 처리
        for (int i = argCount; i < n.el.size(); i++)
            n.el.elementAt(i).accept(this);


        return method.returnType; // Call은 최종적으로 메서드의 반환 타입을 return
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
        if (found == null)
            report(n, "Undeclared identifier: " + n.s);

        return found;
    }

    @Override
    public Type visit(This n) {
        if (currentClass == null) {
            report(n, "this used outside of class context");
            return null;
        }

        return new IdentifierType(currentClass.name);
    }

    @Override
    public Type visit(NewArray n) {
        Type sizeType = n.e.accept(this);
        if (!(sizeType instanceof IntegerType))
            report(n.e, "Array size expression must be int, found " + typeToString(sizeType));


        return new IntArrayType();
    }

    @Override
    public Type visit(NewObject n) {
        if (!classes.containsKey(n.i.s)) // 선언되지 않은 클래스의 객체를 생성하는 경우,
            report(n.i, "Instantiation of undeclared class: " + n.i.s);

        return new IdentifierType(n.i.s);
    }

    @Override
    public Type visit(Not n) {
        Type expr = n.e.accept(this);
        if (!(expr instanceof BooleanType))
            report(n.e, "! expects boolean, but found " + typeToString(expr));


        return new BooleanType();
    }

    @Override
    public Type visit(Identifier n) {
        Type found = lookupVar(n.s);
        if (found == null)
            report(n, "Undeclared identifier: " + n.s);

        return found;
    }
}
