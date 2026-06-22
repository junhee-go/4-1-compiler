package Codegen;

import Assem.*;
import Temp.*;
import Tree.*;
import Mips.Frame;

public class Codegen {
    private InstrList head = null;
    private InstrList last = null;
    private Frame frame;

    public Codegen(Frame f) {
        frame = f;
    }

    private void emit(Instr inst) {
        if (last != null) {
            last = last.tail = new InstrList(inst, null);
        } else {
            head = last = new InstrList(inst, null);
        }
    }

    public InstrList codegen(StmList stms) {
        for (StmList l = stms; l != null; l = l.tail) {
            munchStm(l.head);
        }

        InstrList result = head;
        head = last = null;
        return result;
    }

    private TempList L(Temp h, TempList t) {
        return new TempList(h, t);
    }

    private LabelList L(Label h, LabelList t) {
        return new LabelList(h, t);
    }

    private void munchStm(Stm s) {
        if (s instanceof SEQ) {
            SEQ seq = (SEQ) s;
            munchStm(seq.left);
            munchStm(seq.right);
        }
        else if (s instanceof LABEL) {
            LABEL label = (LABEL) s;
            emit(new Assem.LABEL(label.label.toString() + ":", label.label));
        }
        else if (s instanceof JUMP) {
            JUMP jump = (JUMP) s;
            emit(new OPER("j `j0", null, null, jump.targets));
        }
        else if (s instanceof CJUMP) {
            CJUMP cjump = (CJUMP) s;
            Temp left = munchExp(cjump.left);
            Temp right = munchExp(cjump.right);

            emit(new OPER(branchOp(cjump.relop) + " `s0, `s1, `j0",
                    null,
                    new TempList(left, new TempList(right, null)),
                    new LabelList(cjump.iftrue, new LabelList(cjump.iffalse, null))));
        }
        else if (s instanceof MOVE) {
            MOVE move = (MOVE) s;

            if (move.dst instanceof TEMP) {
                TEMP dst = (TEMP) move.dst;

                if (move.src instanceof CALL) {
                    munchCall((CALL) move.src, dst.temp);
                } else {
                    Temp src = munchExp(move.src);
                    emit(new MOVE("move `d0, `s0",
                            new TempList(dst.temp, null),
                            new TempList(src, null)));
                }
            }
            else if (move.dst instanceof MEM) {
                MEM mem = (MEM) move.dst;
                Addr addr = munchAddr(mem.exp);
                Temp src = munchExp(move.src);

                emit(new OPER("sw `s0, " + addr.offset + "(`s1)",
                        null,
                        new TempList(src, new TempList(addr.base, null))));
            }
        }
        else if (s instanceof EXP) {
            EXP exp = (EXP) s;
            if (exp.exp instanceof CALL) {
                munchCall((CALL) exp.exp, null);
            } else {
                munchExp(exp.exp);
            }
        }
        else {
            throw new Error("Unsupported statement in Codegen: " + s.getClass().getName());
        }
    }

    private Temp munchExp(Exp e) {
        if (e instanceof CONST) {
            CONST c = (CONST) e;
            Temp r = new Temp();
            emit(new OPER("li `d0, " + c.value,
                    new TempList(r, null),
                    null));
            return r;
        }
        else if (e instanceof TEMP) {
            return ((TEMP) e).temp;
        }
        else if (e instanceof NAME) {
            NAME name = (NAME) e;
            Temp r = new Temp();
            emit(new OPER("la `d0, " + name.label,
                    new TempList(r, null),
                    null));
            return r;
        }
        else if (e instanceof MEM) {
            MEM mem = (MEM) e;
            Addr addr = munchAddr(mem.exp);
            Temp r = new Temp();

            emit(new OPER("lw `d0, " + addr.offset + "(`s0)",
                    new TempList(r, null),
                    new TempList(addr.base, null)));
            return r;
        }
        else if (e instanceof BINOP) {
            BINOP binop = (BINOP) e;

            if (binop.right instanceof CONST) {
                Temp left = munchExp(binop.left);
                int value = ((CONST) binop.right).value;
                Temp r = new Temp();

                switch (binop.binop) {
                    case BINOP.PLUS:
                        emit(new OPER("addi `d0, `s0, " + value,
                                new TempList(r, null),
                                new TempList(left, null)));
                        return r;
                    case BINOP.MINUS:
                        emit(new OPER("addi `d0, `s0, " + (-value),
                                new TempList(r, null),
                                new TempList(left, null)));
                        return r;
                }
            }

            Temp left = munchExp(binop.left);
            Temp right = munchExp(binop.right);
            Temp r = new Temp();

            switch (binop.binop) {
                case BINOP.PLUS:
                    emit(new OPER("add `d0, `s0, `s1",
                            new TempList(r, null),
                            new TempList(left, new TempList(right, null))));
                    break;
                case BINOP.MINUS:
                    emit(new OPER("sub `d0, `s0, `s1",
                            new TempList(r, null),
                            new TempList(left, new TempList(right, null))));
                    break;
                case BINOP.MUL:
                    emit(new OPER("mul `d0, `s0, `s1",
                            new TempList(r, null),
                            new TempList(left, new TempList(right, null))));
                    break;
                case BINOP.DIV:
                    emit(new OPER("div `d0, `s0, `s1",
                            new TempList(r, null),
                            new TempList(left, new TempList(right, null))));
                    break;
                default:
                    throw new Error("Unsupported BINOP in Codegen");
            }

            return r;
        }
        else if (e instanceof CALL) {
            Temp r = new Temp();
            munchCall((CALL) e, r);
            return r;
        }

        throw new Error("Unsupported expression in Codegen: " + e.getClass().getName());
    }

    private Temp munchCall(CALL call, Temp resOpt) {
        TempList args = munchArgs(0, call.args);

        if (call.func instanceof NAME) {
            NAME name = (NAME) call.func;
            emit(new OPER("jal " + name.label,
                    frame.calldefs(),
                    args));
        } else {
            Temp func = munchExp(call.func);
            emit(new OPER("jalr `s0",
                    frame.calldefs(),
                    new TempList(func, args)));
        }

        if (resOpt != null) {
            emit(new MOVE("move `d0, `s0",
                    new TempList(resOpt, null),
                    new TempList(frame.RV(), null)));
            return resOpt;
        }

        return frame.RV();
    }

    private TempList munchArgs(int i, ExpList args) {
        if (args == null) return null;

        Temp arg = munchExp(args.head);
        Temp argReg = frame.argReg(i);

        if (argReg != null) {
            emit(new MOVE("move `d0, `s0",
                    new TempList(argReg, null),
                    new TempList(arg, null)));
            return new TempList(argReg, munchArgs(i + 1, args.tail));
        }

        int offset = frame.wordSize() * (i - frame.numArgRegs());
        emit(new OPER("sw `s0, " + offset + "($sp)",
                null,
                new TempList(arg, null)));

        return new TempList(arg, munchArgs(i + 1, args.tail));
    }

    private static class Addr {
        Temp base;
        int offset;

        Addr(Temp base, int offset) {
            this.base = base;
            this.offset = offset;
        }
    }

    private Addr munchAddr(Exp e) {
        if (e instanceof BINOP) {
            BINOP binop = (BINOP) e;

            if (binop.binop == BINOP.PLUS && binop.right instanceof CONST) {
                return new Addr(munchExp(binop.left), ((CONST) binop.right).value);
            }

            if (binop.binop == BINOP.PLUS && binop.left instanceof CONST) {
                return new Addr(munchExp(binop.right), ((CONST) binop.left).value);
            }
        }

        return new Addr(munchExp(e), 0);
    }

    private String branchOp(int relop) {
        switch (relop) {
            case CJUMP.EQ: return "beq";
            case CJUMP.NE: return "bne";
            case CJUMP.LT: return "blt";
            case CJUMP.GT: return "bgt";
            case CJUMP.LE: return "ble";
            case CJUMP.GE: return "bge";
            case CJUMP.ULT: return "bltu";
            case CJUMP.ULE: return "bleu";
            case CJUMP.UGT: return "bgtu";
            case CJUMP.UGE: return "bgeu";
            default:
                throw new Error("Unsupported relop in Codegen: " + relop);
        }
    }
}