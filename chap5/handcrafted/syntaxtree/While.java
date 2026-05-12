package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class While extends Statement {
  public Exp e;
  public Statement s;

  public While(Exp ae, Statement as, int line, int column) {
    super(line, column);
    e=ae; s=as;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}

