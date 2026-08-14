package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.AssignmentExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.BinaryExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.BlockStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.BreakStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.CallExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ConditionalExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ConstDeclaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ContinueStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.Declaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.DiscardStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.Expression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ExpressionStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ForStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.FunctionDeclaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.GodotShaderAst;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.IfStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.IndexExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.LiteralExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.MemberExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.NameExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.Parameter;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.PostfixExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.ReturnStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.Statement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.StructDeclaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.StructMember;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.TypeReference;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.UnaryExpression;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.UniformDeclaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.UniformHint;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.VariableStatement;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.VaryingDeclaration;
import static io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotAst.WhileStatement;

import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded recursive-descent parser with Pratt-style expression precedence. */
final class GodotParser {
    private static final Set<String> PROCESSORS = Set.of("vertex", "fragment", "light");

    private final List<GodotToken> tokens;
    private final ImportLimits limits;
    private int current;
    private int declarationCount;
    private int functionCount;
    private int parameterCount;
    private int statementCount;
    private int expressionCount;

    GodotParser(List<GodotToken> tokens, ImportLimits limits) {
        this.tokens = List.copyOf(tokens);
        this.limits = Objects.requireNonNull(limits, "limits");
        if (this.tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must not be empty");
        }
    }

    GodotShaderAst parse() {
        GodotToken start = consume(GodotTokenKind.SHADER_TYPE,
                "shader must begin with shader_type");
        GodotToken shaderType = consume(GodotTokenKind.IDENTIFIER,
                "shader_type requires a type name");
        consume(GodotTokenKind.SEMICOLON, "shader_type requires a semicolon");
        if (!shaderType.lexeme().equals("canvas_item")) {
            throw failure("UNSUPPORTED_SHADER_TYPE", "only canvas_item shaders are supported",
                    shaderType.span());
        }

        List<String> renderModes = new ArrayList<>();
        List<Declaration> declarations = new ArrayList<>();
        List<FunctionDeclaration> functions = new ArrayList<>();
        Set<String> processors = new HashSet<>();
        while (!check(GodotTokenKind.EOF)) {
            if (match(GodotTokenKind.RENDER_MODE)) {
                parseRenderModes(renderModes);
            } else if (match(GodotTokenKind.UNIFORM)) {
                declarations.add(parseUniform(previous()));
            } else if (match(GodotTokenKind.CONST)) {
                declarations.add(parseConst(previous()));
            } else if (match(GodotTokenKind.STRUCT)) {
                declarations.add(parseStruct(previous()));
            } else if (match(GodotTokenKind.VARYING)) {
                declarations.add(parseVarying(previous()));
            } else {
                FunctionDeclaration function = parseFunction();
                if (PROCESSORS.contains(function.name()) && !processors.add(function.name())) {
                    throw failure("DUPLICATE_PROCESSOR",
                            "shader processor is declared more than once", function.span());
                }
                functions.add(function);
            }
        }
        GodotToken end = consume(GodotTokenKind.EOF, "expected end of shader");
        return new GodotShaderAst(shaderType.lexeme(), renderModes, declarations, functions,
                merge(start.span(), end.span()));
    }

    private void parseRenderModes(List<String> renderModes) {
        do {
            GodotToken mode = consume(GodotTokenKind.IDENTIFIER,
                    "render_mode requires a mode name");
            renderModes.add(mode.lexeme());
            if (renderModes.size() > limits.maxDeclarations()) {
                throw failure("DECLARATION_LIMIT_EXCEEDED",
                        "render mode count exceeds import limits", mode.span());
            }
        } while (match(GodotTokenKind.COMMA));
        consume(GodotTokenKind.SEMICOLON, "render_mode requires a semicolon");
    }

    private UniformDeclaration parseUniform(GodotToken start) {
        countDeclaration(start.span());
        TypeReference type = parseType(false);
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "uniform requires a name");
        type = parseArraySuffix(type);
        UniformHint hint = null;
        if (match(GodotTokenKind.COLON)) {
            GodotToken hintName = consume(GodotTokenKind.IDENTIFIER,
                    "uniform hint requires a name");
            List<Expression> arguments = new ArrayList<>();
            SourceSpan hintSpan = hintName.span();
            if (match(GodotTokenKind.LEFT_PAREN)) {
                GodotToken open = previous();
                if (!check(GodotTokenKind.RIGHT_PAREN)) {
                    do {
                        arguments.add(expression());
                        countParameter(peek().span());
                    } while (match(GodotTokenKind.COMMA));
                }
                GodotToken close = consume(GodotTokenKind.RIGHT_PAREN,
                        "uniform hint requires a closing parenthesis");
                hintSpan = merge(open.span(), close.span());
            }
            hint = new UniformHint(hintName.lexeme(), arguments, hintSpan);
        }
        Expression initializer = match(GodotTokenKind.EQUAL) ? expression() : null;
        GodotToken end = consume(GodotTokenKind.SEMICOLON,
                "uniform declaration requires a semicolon");
        return new UniformDeclaration(type, name.lexeme(), hint, initializer,
                merge(start.span(), end.span()));
    }

    private ConstDeclaration parseConst(GodotToken start) {
        countDeclaration(start.span());
        TypeReference type = parseType(false);
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "const requires a name");
        type = parseArraySuffix(type);
        consume(GodotTokenKind.EQUAL, "const requires an initializer");
        Expression initializer = expression();
        GodotToken end = consume(GodotTokenKind.SEMICOLON,
                "const declaration requires a semicolon");
        return new ConstDeclaration(type, name.lexeme(), initializer,
                merge(start.span(), end.span()));
    }

    private StructDeclaration parseStruct(GodotToken start) {
        countDeclaration(start.span());
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "struct requires a name");
        consume(GodotTokenKind.LEFT_BRACE, "struct requires an opening brace");
        List<StructMember> members = new ArrayList<>();
        while (!check(GodotTokenKind.RIGHT_BRACE) && !check(GodotTokenKind.EOF)) {
            TypeReference type = parseType(false);
            GodotToken memberName = consume(GodotTokenKind.IDENTIFIER,
                    "struct member requires a name");
            type = parseArraySuffix(type);
            GodotToken end = consume(GodotTokenKind.SEMICOLON,
                    "struct member requires a semicolon");
            countDeclaration(memberName.span());
            members.add(new StructMember(type, memberName.lexeme(),
                    merge(type.span(), end.span())));
        }
        GodotToken close = consume(GodotTokenKind.RIGHT_BRACE,
                "struct requires a closing brace");
        if (match(GodotTokenKind.SEMICOLON)) {
            close = previous();
        }
        return new StructDeclaration(name.lexeme(), members, merge(start.span(), close.span()));
    }

    private VaryingDeclaration parseVarying(GodotToken start) {
        countDeclaration(start.span());
        TypeReference type = parseType(false);
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "varying requires a name");
        type = parseArraySuffix(type);
        GodotToken end = consume(GodotTokenKind.SEMICOLON,
                "varying declaration requires a semicolon");
        return new VaryingDeclaration(type, name.lexeme(), merge(start.span(), end.span()));
    }

    private FunctionDeclaration parseFunction() {
        TypeReference returnType = parseType(true);
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "function requires a name");
        countFunction(name.span());
        consume(GodotTokenKind.LEFT_PAREN, "function requires an opening parenthesis");
        List<Parameter> parameters = new ArrayList<>();
        if (!check(GodotTokenKind.RIGHT_PAREN)) {
            do {
                GodotToken start = peek();
                String qualifier = "";
                if (match(GodotTokenKind.IN, GodotTokenKind.OUT, GodotTokenKind.INOUT)) {
                    qualifier = previous().lexeme();
                }
                TypeReference type = parseType(false);
                GodotToken parameterName = consume(GodotTokenKind.IDENTIFIER,
                        "parameter requires a name");
                type = parseArraySuffix(type);
                countParameter(parameterName.span());
                parameters.add(new Parameter(qualifier, type, parameterName.lexeme(),
                        merge(start.span(), type.span())));
            } while (match(GodotTokenKind.COMMA));
        }
        consume(GodotTokenKind.RIGHT_PAREN, "function requires a closing parenthesis");
        GodotToken open = consume(GodotTokenKind.LEFT_BRACE,
                "function requires an opening brace");
        BlockStatement body = parseBlock(open, 1);
        return new FunctionDeclaration(returnType, name.lexeme(), parameters, body,
                merge(returnType.span(), body.span()));
    }

    private BlockStatement parseBlock(GodotToken open, int depth) {
        if (depth > limits.maxAstDepth()) {
            throw failure("AST_DEPTH_EXCEEDED", "shader AST nesting exceeds import limits",
                    open.span());
        }
        List<Statement> statements = new ArrayList<>();
        while (!check(GodotTokenKind.RIGHT_BRACE) && !check(GodotTokenKind.EOF)) {
            statements.add(parseStatement(depth));
        }
        GodotToken close = consume(GodotTokenKind.RIGHT_BRACE,
                "block requires a closing brace");
        return new BlockStatement(statements, merge(open.span(), close.span()));
    }

    private Statement parseStatement(int depth) {
        if (match(GodotTokenKind.LEFT_BRACE)) {
            return countStatement(parseBlock(previous(), depth + 1));
        }
        if (match(GodotTokenKind.IF)) {
            return parseIf(previous(), depth);
        }
        if (match(GodotTokenKind.FOR)) {
            return parseFor(previous(), depth);
        }
        if (match(GodotTokenKind.WHILE)) {
            return parseWhile(previous(), depth);
        }
        if (match(GodotTokenKind.RETURN)) {
            GodotToken start = previous();
            Expression value = check(GodotTokenKind.SEMICOLON) ? null : expression();
            GodotToken end = consume(GodotTokenKind.SEMICOLON,
                    "return requires a semicolon");
            return countStatement(new ReturnStatement(value, merge(start.span(), end.span())));
        }
        if (match(GodotTokenKind.BREAK)) {
            GodotToken start = previous();
            GodotToken end = consume(GodotTokenKind.SEMICOLON,
                    "break requires a semicolon");
            return countStatement(new BreakStatement(merge(start.span(), end.span())));
        }
        if (match(GodotTokenKind.CONTINUE)) {
            GodotToken start = previous();
            GodotToken end = consume(GodotTokenKind.SEMICOLON,
                    "continue requires a semicolon");
            return countStatement(new ContinueStatement(merge(start.span(), end.span())));
        }
        if (match(GodotTokenKind.DISCARD)) {
            GodotToken start = previous();
            GodotToken end = consume(GodotTokenKind.SEMICOLON,
                    "discard requires a semicolon");
            return countStatement(new DiscardStatement(merge(start.span(), end.span())));
        }
        if (check(GodotTokenKind.IDENTIFIER)
                && checkNext(GodotTokenKind.IDENTIFIER)) {
            return parseVariable(true);
        }
        GodotToken start = peek();
        Expression value = expression();
        GodotToken end = consume(GodotTokenKind.SEMICOLON,
                "expression requires a semicolon");
        return countStatement(new ExpressionStatement(value, merge(start.span(), end.span())));
    }

    private Statement parseVariable(boolean consumeSemicolon) {
        TypeReference type = parseType(false);
        GodotToken name = consume(GodotTokenKind.IDENTIFIER, "variable requires a name");
        type = parseArraySuffix(type);
        Expression initializer = match(GodotTokenKind.EQUAL) ? expression() : null;
        SourceSpan endSpan = type.span();
        if (consumeSemicolon) {
            endSpan = consume(GodotTokenKind.SEMICOLON,
                    "variable declaration requires a semicolon").span();
        }
        return countStatement(new VariableStatement(type, name.lexeme(), initializer,
                merge(type.span(), endSpan)));
    }

    private IfStatement parseIf(GodotToken start, int depth) {
        consume(GodotTokenKind.LEFT_PAREN, "if requires an opening parenthesis");
        Expression condition = expression();
        consume(GodotTokenKind.RIGHT_PAREN, "if requires a closing parenthesis");
        Statement thenBranch = parseStatement(depth);
        Statement elseBranch = match(GodotTokenKind.ELSE) ? parseStatement(depth) : null;
        SourceSpan end = elseBranch == null ? thenBranch.span() : elseBranch.span();
        return countStatement(new IfStatement(
                condition, thenBranch, elseBranch, merge(start.span(), end)));
    }

    private ForStatement parseFor(GodotToken start, int depth) {
        consume(GodotTokenKind.LEFT_PAREN, "for requires an opening parenthesis");
        Statement initializer = null;
        if (!match(GodotTokenKind.SEMICOLON)) {
            if (check(GodotTokenKind.IDENTIFIER)
                    && checkNext(GodotTokenKind.IDENTIFIER)) {
                initializer = parseVariable(true);
            } else {
                GodotToken expressionStart = peek();
                Expression expression = expression();
                GodotToken end = consume(GodotTokenKind.SEMICOLON,
                        "for initializer requires a semicolon");
                initializer = countStatement(new ExpressionStatement(expression,
                        merge(expressionStart.span(), end.span())));
            }
        }
        Expression condition = check(GodotTokenKind.SEMICOLON) ? null : expression();
        consume(GodotTokenKind.SEMICOLON, "for condition requires a semicolon");
        Expression update = check(GodotTokenKind.RIGHT_PAREN) ? null : expression();
        consume(GodotTokenKind.RIGHT_PAREN, "for requires a closing parenthesis");
        Statement body = parseStatement(depth);
        return countStatement(new ForStatement(
                initializer, condition, update, body, merge(start.span(), body.span())));
    }

    private WhileStatement parseWhile(GodotToken start, int depth) {
        consume(GodotTokenKind.LEFT_PAREN, "while requires an opening parenthesis");
        Expression condition = expression();
        consume(GodotTokenKind.RIGHT_PAREN, "while requires a closing parenthesis");
        Statement body = parseStatement(depth);
        return countStatement(new WhileStatement(
                condition, body, merge(start.span(), body.span())));
    }

    private Expression expression() {
        return assignment();
    }

    private Expression assignment() {
        Expression left = conditional();
        if (match(GodotTokenKind.EQUAL, GodotTokenKind.PLUS_EQUAL,
                GodotTokenKind.MINUS_EQUAL, GodotTokenKind.STAR_EQUAL,
                GodotTokenKind.SLASH_EQUAL, GodotTokenKind.PERCENT_EQUAL,
                GodotTokenKind.AND_EQUAL, GodotTokenKind.OR_EQUAL,
                GodotTokenKind.XOR_EQUAL, GodotTokenKind.SHIFT_LEFT_EQUAL,
                GodotTokenKind.SHIFT_RIGHT_EQUAL)) {
            GodotToken operator = previous();
            Expression value = assignment();
            return countExpression(new AssignmentExpression(left, operator.lexeme(), value,
                    merge(left.span(), value.span())));
        }
        return left;
    }

    private Expression conditional() {
        Expression condition = binary(1);
        if (!match(GodotTokenKind.QUESTION)) {
            return condition;
        }
        Expression whenTrue = expression();
        consume(GodotTokenKind.COLON, "conditional expression requires a colon");
        Expression whenFalse = conditional();
        return countExpression(new ConditionalExpression(
                condition, whenTrue, whenFalse, merge(condition.span(), whenFalse.span())));
    }

    private Expression binary(int minimumPrecedence) {
        Expression left = unary();
        while (precedence(peek().kind()) >= minimumPrecedence) {
            GodotToken operator = advance();
            int operatorPrecedence = precedence(operator.kind());
            Expression right = binary(operatorPrecedence + 1);
            left = countExpression(new BinaryExpression(left, operator.lexeme(), right,
                    merge(left.span(), right.span())));
        }
        return left;
    }

    private Expression unary() {
        if (match(GodotTokenKind.BANG, GodotTokenKind.TILDE, GodotTokenKind.PLUS,
                GodotTokenKind.MINUS, GodotTokenKind.PLUS_PLUS,
                GodotTokenKind.MINUS_MINUS)) {
            GodotToken operator = previous();
            Expression operand = unary();
            return countExpression(new UnaryExpression(operator.lexeme(), operand,
                    merge(operator.span(), operand.span())));
        }
        return postfix();
    }

    private Expression postfix() {
        Expression value = primary();
        boolean scanning = true;
        while (scanning) {
            if (match(GodotTokenKind.LEFT_PAREN)) {
                List<Expression> arguments = new ArrayList<>();
                if (!check(GodotTokenKind.RIGHT_PAREN)) {
                    do {
                        arguments.add(expression());
                        countParameter(peek().span());
                    } while (match(GodotTokenKind.COMMA));
                }
                GodotToken close = consume(GodotTokenKind.RIGHT_PAREN,
                        "call requires a closing parenthesis");
                value = countExpression(new CallExpression(value, arguments,
                        merge(value.span(), close.span())));
            } else if (match(GodotTokenKind.DOT)) {
                GodotToken member = consume(GodotTokenKind.IDENTIFIER,
                        "member access requires a name");
                value = countExpression(new MemberExpression(value, member.lexeme(),
                        merge(value.span(), member.span())));
            } else if (match(GodotTokenKind.LEFT_BRACKET)) {
                Expression index = expression();
                GodotToken close = consume(GodotTokenKind.RIGHT_BRACKET,
                        "index requires a closing bracket");
                value = countExpression(new IndexExpression(value, index,
                        merge(value.span(), close.span())));
            } else if (match(GodotTokenKind.PLUS_PLUS, GodotTokenKind.MINUS_MINUS)) {
                GodotToken operator = previous();
                value = countExpression(new PostfixExpression(value, operator.lexeme(),
                        merge(value.span(), operator.span())));
            } else {
                scanning = false;
            }
        }
        return value;
    }

    private Expression primary() {
        if (match(GodotTokenKind.INTEGER_LITERAL, GodotTokenKind.FLOAT_LITERAL,
                GodotTokenKind.BOOLEAN_LITERAL)) {
            GodotToken literal = previous();
            return countExpression(new LiteralExpression(literal.lexeme(), literal.span()));
        }
        if (match(GodotTokenKind.IDENTIFIER)) {
            GodotToken name = previous();
            return countExpression(new NameExpression(name.lexeme(), name.span()));
        }
        if (match(GodotTokenKind.LEFT_PAREN)) {
            GodotToken open = previous();
            Expression nested = expression();
            GodotToken close = consume(GodotTokenKind.RIGHT_PAREN,
                    "grouped expression requires a closing parenthesis");
            return countExpression(new UnaryExpression("()", nested,
                    merge(open.span(), close.span())));
        }
        throw failure("EXPECTED_EXPRESSION", "expected shader expression", peek().span());
    }

    private TypeReference parseType(boolean allowVoid) {
        if (allowVoid && match(GodotTokenKind.VOID)) {
            GodotToken type = previous();
            return new TypeReference(type.lexeme(), List.of(), type.span());
        }
        GodotToken type = consume(GodotTokenKind.IDENTIFIER, "expected a type name");
        return new TypeReference(type.lexeme(), List.of(), type.span());
    }

    private TypeReference parseArraySuffix(TypeReference type) {
        List<Integer> sizes = new ArrayList<>();
        SourceSpan end = type.span();
        while (match(GodotTokenKind.LEFT_BRACKET)) {
            GodotToken size = consume(GodotTokenKind.INTEGER_LITERAL,
                    "array requires a constant integer size");
            int parsed;
            try {
                parsed = Integer.decode(size.lexeme());
            } catch (NumberFormatException failure) {
                throw failure("INVALID_ARRAY_SIZE", "array size is invalid", size.span());
            }
            if (parsed <= 0 || parsed > limits.maxParameters()) {
                throw failure("ARRAY_LIMIT_EXCEEDED", "array size exceeds import limits",
                        size.span());
            }
            sizes.add(parsed);
            end = consume(GodotTokenKind.RIGHT_BRACKET,
                    "array requires a closing bracket").span();
        }
        return sizes.isEmpty() ? type
                : new TypeReference(type.name(), sizes, merge(type.span(), end));
    }

    private <T extends Statement> T countStatement(T statement) {
        statementCount++;
        if (statementCount > limits.maxStatements()) {
            throw failure("STATEMENT_LIMIT_EXCEEDED",
                    "shader statement count exceeds import limits", statement.span());
        }
        return statement;
    }

    private <T extends Expression> T countExpression(T expression) {
        expressionCount++;
        if (expressionCount > limits.maxExpressionNodes()) {
            throw failure("EXPRESSION_LIMIT_EXCEEDED",
                    "shader expression count exceeds import limits", expression.span());
        }
        return expression;
    }

    private void countDeclaration(SourceSpan span) {
        declarationCount++;
        if (declarationCount > limits.maxDeclarations()) {
            throw failure("DECLARATION_LIMIT_EXCEEDED",
                    "shader declaration count exceeds import limits", span);
        }
    }

    private void countFunction(SourceSpan span) {
        functionCount++;
        if (functionCount > limits.maxFunctions()) {
            throw failure("FUNCTION_LIMIT_EXCEEDED",
                    "shader function count exceeds import limits", span);
        }
    }

    private void countParameter(SourceSpan span) {
        parameterCount++;
        if (parameterCount > limits.maxParameters()) {
            throw failure("PARAMETER_LIMIT_EXCEEDED",
                    "shader parameter count exceeds import limits", span);
        }
    }

    private GodotToken consume(GodotTokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        throw failure("EXPECTED_TOKEN", message, peek().span());
    }

    private boolean match(GodotTokenKind... kinds) {
        for (GodotTokenKind kind : kinds) {
            if (check(kind)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(GodotTokenKind kind) {
        return peek().kind() == kind;
    }

    private boolean checkNext(GodotTokenKind kind) {
        return current + 1 < tokens.size() && tokens.get(current + 1).kind() == kind;
    }

    private GodotToken advance() {
        if (!check(GodotTokenKind.EOF)) {
            current++;
        }
        return previous();
    }

    private GodotToken peek() {
        return tokens.get(current);
    }

    private GodotToken previous() {
        return tokens.get(current - 1);
    }

    private GodotImportException failure(String code, String message, SourceSpan span) {
        return new GodotImportException(code, message, span);
    }

    private static SourceSpan merge(SourceSpan start, SourceSpan end) {
        return new SourceSpan(start.startLine(), start.startColumn(),
                end.endLine(), end.endColumn(), start.startOffset(), end.endOffset());
    }

    private static int precedence(GodotTokenKind kind) {
        return switch (kind) {
            case OR_OR -> 1;
            case AND_AND -> 2;
            case OR -> 3;
            case XOR -> 4;
            case AND -> 5;
            case EQUAL_EQUAL, BANG_EQUAL -> 6;
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> 7;
            case SHIFT_LEFT, SHIFT_RIGHT -> 8;
            case PLUS, MINUS -> 9;
            case STAR, SLASH, PERCENT -> 10;
            default -> -1;
        };
    }
}
