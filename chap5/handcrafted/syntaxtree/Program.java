package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class Program extends ASTNode {
  public MainClass m;
  public ClassDeclList cl;

  public Program(MainClass am, ClassDeclList acl, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    m=am; cl=acl;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
