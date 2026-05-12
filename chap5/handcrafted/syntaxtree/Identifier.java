package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class Identifier extends ASTNode {
  public String s;

  public Identifier(String as, int line, int column, int endLine, int endColumn) {
    super(line, column, endLine, endColumn);
    s=as;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }

  public String toString(){
    return s;
  }
}
