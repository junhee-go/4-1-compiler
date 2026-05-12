package syntaxtree;
import visitor.Visitor;
import visitor.TypeVisitor;

public class Block extends Statement {
  public StatementList sl;

  public Block(StatementList asl, int line, int column) {
    super(line, column);
    sl=asl;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Type accept(TypeVisitor v) {
    return v.visit(this);
  }
}

