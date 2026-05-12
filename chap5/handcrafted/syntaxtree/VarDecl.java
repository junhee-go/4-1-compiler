package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class VarDecl extends ASTNode {
  public Type t;
  public Identifier i;
  
  public VarDecl(Type at, Identifier ai, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    t=at; i=ai;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}
