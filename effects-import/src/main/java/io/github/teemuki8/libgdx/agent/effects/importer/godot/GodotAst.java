package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.List;
import java.util.Objects;

/** Package-private immutable AST nodes for the supported Godot shader grammar. */
final class GodotAst {
    private GodotAst() {}

record GodotShaderAst(
        String shaderType,
        List<String> renderModes,
        List<Declaration> declarations,
        List<FunctionDeclaration> functions,
        SourceSpan span) {
    GodotShaderAst {
        Objects.requireNonNull(shaderType, "shaderType");
        renderModes = List.copyOf(renderModes);
        declarations = List.copyOf(declarations);
        functions = List.copyOf(functions);
        Objects.requireNonNull(span, "span");
    }
}

record TypeReference(String name, List<Integer> arraySizes, SourceSpan span) {
    TypeReference {
        Objects.requireNonNull(name, "name");
        arraySizes = List.copyOf(arraySizes);
        Objects.requireNonNull(span, "span");
    }
}

sealed interface Declaration permits UniformDeclaration, ConstDeclaration, StructDeclaration,
        VaryingDeclaration {
    SourceSpan span();
}

record UniformHint(String name, List<Expression> arguments, SourceSpan span) {
    UniformHint {
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(span, "span");
    }
}

record UniformDeclaration(
        TypeReference type,
        String name,
        UniformHint hint,
        Expression initializer,
        SourceSpan span) implements Declaration {
    UniformDeclaration {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record ConstDeclaration(
        TypeReference type,
        String name,
        Expression initializer,
        SourceSpan span) implements Declaration {
    ConstDeclaration {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(span, "span");
    }
}

record StructMember(TypeReference type, String name, SourceSpan span) {
    StructMember {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record StructDeclaration(
        String name,
        List<StructMember> members,
        SourceSpan span) implements Declaration {
    StructDeclaration {
        Objects.requireNonNull(name, "name");
        members = List.copyOf(members);
        Objects.requireNonNull(span, "span");
    }
}

record VaryingDeclaration(
        TypeReference type,
        String name,
        SourceSpan span) implements Declaration {
    VaryingDeclaration {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record Parameter(String qualifier, TypeReference type, String name, SourceSpan span) {
    Parameter {
        Objects.requireNonNull(qualifier, "qualifier");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record FunctionDeclaration(
        TypeReference returnType,
        String name,
        List<Parameter> parameters,
        BlockStatement body,
        SourceSpan span) {
    FunctionDeclaration {
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(name, "name");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}

sealed interface Statement permits BlockStatement, VariableStatement, ExpressionStatement,
        IfStatement, ForStatement, WhileStatement, ReturnStatement, BreakStatement,
        ContinueStatement, DiscardStatement {
    SourceSpan span();
}

record BlockStatement(List<Statement> statements, SourceSpan span) implements Statement {
    BlockStatement {
        statements = List.copyOf(statements);
        Objects.requireNonNull(span, "span");
    }
}

record VariableStatement(
        TypeReference type,
        String name,
        Expression initializer,
        SourceSpan span) implements Statement {
    VariableStatement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record ExpressionStatement(Expression expression, SourceSpan span) implements Statement {
    ExpressionStatement {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }
}

record IfStatement(
        Expression condition,
        Statement thenBranch,
        Statement elseBranch,
        SourceSpan span) implements Statement {
    IfStatement {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenBranch, "thenBranch");
        Objects.requireNonNull(span, "span");
    }
}

record ForStatement(
        Statement initializer,
        Expression condition,
        Expression update,
        Statement body,
        SourceSpan span) implements Statement {
    ForStatement {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}

record WhileStatement(Expression condition, Statement body, SourceSpan span) implements Statement {
    WhileStatement {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}

record ReturnStatement(Expression value, SourceSpan span) implements Statement {
    ReturnStatement {
        Objects.requireNonNull(span, "span");
    }
}

record BreakStatement(SourceSpan span) implements Statement {
    BreakStatement {
        Objects.requireNonNull(span, "span");
    }
}

record ContinueStatement(SourceSpan span) implements Statement {
    ContinueStatement {
        Objects.requireNonNull(span, "span");
    }
}

record DiscardStatement(SourceSpan span) implements Statement {
    DiscardStatement {
        Objects.requireNonNull(span, "span");
    }
}

sealed interface Expression permits LiteralExpression, NameExpression, UnaryExpression,
        BinaryExpression, ConditionalExpression, AssignmentExpression, CallExpression,
        MemberExpression, IndexExpression, PostfixExpression {
    SourceSpan span();
}

record LiteralExpression(String lexeme, SourceSpan span) implements Expression {
    LiteralExpression {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(span, "span");
    }
}

record NameExpression(String name, SourceSpan span) implements Expression {
    NameExpression {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}

record UnaryExpression(String operator, Expression operand, SourceSpan span) implements Expression {
    UnaryExpression {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(span, "span");
    }
}

record BinaryExpression(
        Expression left,
        String operator,
        Expression right,
        SourceSpan span) implements Expression {
    BinaryExpression {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(span, "span");
    }
}

record ConditionalExpression(
        Expression condition,
        Expression whenTrue,
        Expression whenFalse,
        SourceSpan span) implements Expression {
    ConditionalExpression {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(whenTrue, "whenTrue");
        Objects.requireNonNull(whenFalse, "whenFalse");
        Objects.requireNonNull(span, "span");
    }
}

record AssignmentExpression(
        Expression target,
        String operator,
        Expression value,
        SourceSpan span) implements Expression {
    AssignmentExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}

record CallExpression(
        Expression callee,
        List<Expression> arguments,
        SourceSpan span) implements Expression {
    CallExpression {
        Objects.requireNonNull(callee, "callee");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(span, "span");
    }
}

record MemberExpression(Expression target, String member, SourceSpan span) implements Expression {
    MemberExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(span, "span");
    }
}

record IndexExpression(Expression target, Expression index, SourceSpan span) implements Expression {
    IndexExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(span, "span");
    }
}

record PostfixExpression(Expression operand, String operator, SourceSpan span)
        implements Expression {
    PostfixExpression {
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(span, "span");
    }
}
}
