import syntaxtree.*;
import visitor.*;

public class Main {
    public static void main(String[] args) {
        for (String path : args) {
            try {
                java.io.InputStream in = new java.io.FileInputStream(path);
                MiniJavaParser parser = new MiniJavaParser(in); // 입력된 프로그램에 대한 parser 생성
                Program root = parser.Goal(); // Parsing 시작 => parser returns AST( Program Node -> Root )
                boolean success = new TypeCheckVisitor().check(root); // 생성된 AST를 가지고 Type Checking을 시작
                if (success)
                    System.out.println("Successfully type checked for " + path);
                else
                    System.out.println("Type Check Failed for " + path);
            }
            catch (ParseException e) {
                System.err.println("Parse error in " + path + ":\n" + e.toString());
                System.exit(2);
            }
            catch (java.io.IOException e) {
                System.err.println("Failed in opening file for " + path);
            }
        }
    }
}
