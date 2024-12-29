package yan.lx.bedrockminer.command.argument.operator;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum Operator {
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    EQUAL("=="),
    LESS_THAN_OR_EQUAL("<="),
    LESS_THAN("<");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static @Nullable Operator fromString(String symbol) {
        for (var op : values()) {
            if (op.getSymbol().equals(symbol)) {
                return op;
            }
        }
        return null;
    }

    public static List<String> getStringValues() {
        var list = new ArrayList<String>();
        for (var operator : Operator.values()) {
            list.add(operator.symbol);
        }
        return list;
    }
}

