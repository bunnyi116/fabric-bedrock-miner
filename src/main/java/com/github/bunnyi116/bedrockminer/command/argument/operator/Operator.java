package com.github.bunnyi116.bedrockminer.command.argument.operator;

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
        for (Operator op : values()) {
            if (op.getSymbol().equals(symbol)) {
                return op;
            }
        }
        return null;
    }

    public static List<String> getStringValues() {
        ArrayList<String> list = new ArrayList<>();
        for (Operator operator : Operator.values()) {
            list.add(operator.symbol);
        }
        return list;
    }
}

