package com.timsystem;

import com.timsystem.ast.*;
import com.timsystem.lib.SPKException;
import com.timsystem.lib.Token;
import com.timsystem.lib.TokenType;
import jdk.nashorn.internal.ir.Block;

import java.util.ArrayList;
import java.util.List;


public final class Parser {
    private static final Token EOF = new Token(TokenType.EOF, "");

    private final List<Token> tokens;
    private final int size;

    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        size = tokens.size();
    }

    public BlockStatement parse() {
        final BlockStatement result = new BlockStatement();
        while (!match(TokenType.EOF)) {
            result.add(statement());
        }
        return result;
    }

    private Statement block(){
        final BlockStatement block = new BlockStatement();
        consume(TokenType.LBRACE);
        while (!match(TokenType.RBRACE)) {
            block.add(statement());
        }
        return block;
    }
    private Statement statement() {
        Token current = get(0);
        if (match(TokenType.OUT)) {
            return outStatement();
        }
        else if (match(TokenType.ADD)) {
            return new AddStatement(consume(TokenType.WORD).getText());
        }
        else if (match(TokenType.VAR)) {
            return assignmentStatement();
        }
        else if (match(TokenType.IF)) {
            return ifElse();
        }
        else if(match(TokenType.WHILE)){
            return whileStatement();
        }
        return reAssignmentStatement();
    }

    private Statement reAssignmentStatement() {
        final Token current = get(0);
        if (current.getType() == TokenType.WORD && get(1).getType() == TokenType.EQ) {
            final String variable = consume(TokenType.WORD).getText();
            consume(TokenType.EQ);
            return new ReAssignmentStatement(variable, expression());
        }
        throw new SPKException("StatementError", String.format("unknown statement '%s'", current.getType()));
    }

    private Statement outStatement() {
        consume(TokenType.COLON);
        return new OutStatement(expression());
    }

    private Statement assignmentStatement() {
        final Token current = get(0);
        final String variable = consume(TokenType.WORD).getText();
        consume(TokenType.EQ);
        return new AssignmentStatement(variable, expression());
    }
    private Statement statementOrBlock(){
        if(get(0).getType() == TokenType.LBRACE) return block();
        else return statement();
    }
    private Statement ifElse() {
        final Expression conditional = expression();
        final Statement ifStatement = statementOrBlock();
        final Statement elseStatement;
        final Token current = get(0);
        if (match(TokenType.ELSE)) {
            elseStatement = statementOrBlock();
        } else {
            elseStatement = null;
        }
        return new IfStatement(conditional, ifStatement, elseStatement);
    }
    private Statement whileStatement(){
        final Expression conditional = expression();
        final Statement statement = statementOrBlock();
        return new WhileStatement(conditional, statement);
    }
    private Expression expression() {
        return logicIn();
    }

    private Expression logicIn() {
        Expression expr = logicOr();

        if (match(TokenType.NOT) && match(TokenType.IN)) {
            return new UnaryExpression('!', new InExpression(expr, logicOr()));
        }

        if (match(TokenType.IN)) {
            return new InExpression(expr, expression());
        }
        return expr;
    }

    private Expression logicOr() {
        Expression result = logicAnd();
        while (true) {
            if (match(TokenType.OR)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.OR, result, logicAnd());
                continue;
            }
            break;
        }
        return result;
    }

    private Expression logicAnd() {
        Expression result = equality();
        while (true) {
            if (match(TokenType.AND)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.AND, result, equality());
                continue;
            }
            break;
        }
        return result;
    }

    private Expression equality() {
        Expression result = conditional();
        if (match(TokenType.EQEQ)) {
            return new ConditionalExpression(ConditionalExpression.Operator.EQUALS, result, conditional());
        }

        if (match(TokenType.NOTEQ)) {
            return new ConditionalExpression(ConditionalExpression.Operator.NOT_EQUALS, result, conditional());
        } else return result;
    }

    private Expression conditional() {
        Expression result = additive();

        while (true) {
            if (match(TokenType.LTEQ)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.LTEQ, result, additive());
                continue;
            } else if (match(TokenType.GTEQ)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.GTEQ, result, additive());
                continue;
            } else if (match(TokenType.LT)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.LT, result, additive());
                continue;
            } else if (match(TokenType.GT)) {
                result = new ConditionalExpression(ConditionalExpression.Operator.GT, result, additive());
                continue;
            }
            break;
        }

        return result;
    }

    private Expression additive() {
        Expression result = multiplicative();

        while (true) {
            if (match(TokenType.PLUS)) {
                result = new BinaryExpression('+', result, multiplicative());
                continue;
            }
            if (match(TokenType.MINUS)) {
                result = new BinaryExpression('-', result, multiplicative());
                continue;
            }
            break;
        }

        return result;
    }

    private Expression multiplicative() {
        Expression result = unary();

        while (true) {
            if (match(TokenType.STAR)) {
                result = new BinaryExpression('*', result, unary());
                continue;
            }
            if (match(TokenType.SLASH)) {
                result = new BinaryExpression('/', result, unary());
                continue;
            }
            break;
        }

        return result;
    }

    private Expression unary() {
        if (match(TokenType.MINUS)) {
            return new UnaryExpression('-', primary());
        }

        if (match(TokenType.NOT)) {
            return new UnaryExpression('!', primary());
        }

        return primary();
    }

    private Expression primary() {
        final Token current = get(0);
        if (match(TokenType.NUMBER)) {
            return new ValueExpression(createNumber(current.getText(), 32));
        } else if (match(TokenType.HEX_NUMBER)) {
            return new ValueExpression(createNumber(current.getText(), 32));
        } else if (match(TokenType.WORD)) {
            return new VariableExpression(current.getText());
        } else if (match(TokenType.STRING)) {
            return new ValueExpression(current.getText());
        } else if (match(TokenType.LPAREN)) {
            Expression result = expression();
            match(TokenType.RPAREN);
            return result;
        }
        throw new SPKException("ExpressionError", String.format("unknown expression '%s'", current.getType()));
    }

    private Number createNumber(String text, int radix) {
        // Double
        if (text.contains(".")) {
            return Double.parseDouble(text);
        }
        // Integer
        try {
            return Integer.parseInt(text, radix);
        } catch (NumberFormatException nfe) {
            return Long.parseLong(text, radix);
        }
    }

    private Token consume(TokenType type) {
        final Token current = get(0);
        if (type != current.getType())
            throw new SPKException("TokenError", "Token '" + current.getText() + "' doesn't match " + type);
        //    throw new RuntimeException("Token " + current + " doesn't match " + type);
        pos++;
        return current;
    }

    private boolean match(TokenType type) {
        final Token current = get(0);
        if (type != current.getType()) return false;
        pos++;
        return true;
    }

    private Token get(int relativePosition) {
        final int position = pos + relativePosition;
        if (position >= size) return EOF;
        return tokens.get(position);
    }
}