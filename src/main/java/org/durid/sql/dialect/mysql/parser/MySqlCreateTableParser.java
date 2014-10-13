/*
 * Copyright 1999-2011 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.durid.sql.dialect.mysql.parser;

import org.durid.sql.ast.SQLName;
import org.durid.sql.ast.statement.SQLColumnDefinition;
import org.durid.sql.ast.statement.SQLCreateTableStatement;
import org.durid.sql.ast.statement.SQLSelect;
import org.durid.sql.ast.statement.SQLTableConstaint;
import org.durid.sql.dialect.mysql.ast.MySqlKey;
import org.durid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import org.durid.sql.dialect.mysql.ast.statement.MySqlPartitionByKey;
import org.durid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import org.durid.sql.parser.ParserException;
import org.durid.sql.parser.SQLCreateTableParser;
import org.durid.sql.parser.SQLExprParser;
import org.durid.sql.parser.Token;

public class MySqlCreateTableParser extends SQLCreateTableParser {

    public MySqlCreateTableParser(String sql) {
        super(new MySqlExprParser(sql));
    }

    public MySqlCreateTableParser(SQLExprParser exprParser){
        super(exprParser);
    }

    public SQLCreateTableStatement parseCrateTable() {
        return parseCrateTable(true);
    }

    public MySqlCreateTableStatement parseCrateTable(boolean acceptCreate) {
        if (acceptCreate) {
            accept(Token.CREATE);
        }
        MySqlCreateTableStatement stmt = new MySqlCreateTableStatement();

        if (identifierEquals("TEMPORARY")) {
            lexer.nextToken();
            stmt.setType(SQLCreateTableStatement.Type.GLOBAL_TEMPORARY);
        }

        accept(Token.TABLE);

        if (lexer.token() == Token.IF || identifierEquals("IF")) {
            lexer.nextToken();
            accept(Token.NOT);
            accept(Token.EXISTS);

            stmt.setIfNotExiists(true);
        }

        stmt.setName(this.exprParser.name());

        if (lexer.token() == (Token.LPAREN)) {
            lexer.nextToken();

            for (;;) {
                if (lexer.token() == Token.IDENTIFIER) {
                    SQLColumnDefinition column = this.exprParser.parseColumn();
                    stmt.getTableElementList().add(column);
                } else if (lexer.token() == (Token.CONSTRAINT)) {
                    stmt.getTableElementList().add(parseConstraint());
                } else if (lexer.token() == (Token.INDEX)) {
                    lexer.nextToken();

                    MySqlTableIndex idx = new MySqlTableIndex();

                    if (lexer.token() == Token.IDENTIFIER) {
                        if (!"USING".equalsIgnoreCase(lexer.stringVal())) {
                            idx.setName(this.exprParser.name());
                        }
                    }

                    if (identifierEquals("USING")) {
                        lexer.nextToken();
                        idx.setIndexType(lexer.stringVal());
                        lexer.nextToken();
                    }

                    accept(Token.LPAREN);
                    for (;;) {
                        idx.getColumns().add(this.exprParser.expr());
                        if (!(lexer.token() == (Token.COMMA))) {
                            break;
                        } else {
                            lexer.nextToken();
                        }
                    }
                    accept(Token.RPAREN);

                    stmt.getTableElementList().add(idx);
                } else if (lexer.token() == (Token.KEY)) {
                    stmt.getTableElementList().add(parseConstraint());
                } else if (lexer.token() == (Token.PRIMARY)) {
                    stmt.getTableElementList().add(parseConstraint());
                }

                if (!(lexer.token() == (Token.COMMA))) {
                    break;
                } else {
                    lexer.nextToken();
                }
            }

            accept(Token.RPAREN);
        }

        for (;;) {
            if (identifierEquals("ENGINE")) {
                lexer.nextToken();
                accept(Token.EQ);
                stmt.getTableOptions().put("ENGINE", lexer.stringVal());
                lexer.nextToken();
                continue;
            }

            if (identifierEquals("TYPE")) {
                lexer.nextToken();
                accept(Token.EQ);
                stmt.getTableOptions().put("TYPE", lexer.stringVal());
                lexer.nextToken();
                continue;
            }

            if (identifierEquals("PARTITION")) {
                lexer.nextToken();
                accept(Token.BY);

                if (lexer.token() == Token.KEY) {
                    MySqlPartitionByKey clause = new MySqlPartitionByKey();
                    lexer.nextToken();
                    accept(Token.LPAREN);
                    for (;;) {
                        clause.getColumns().add(this.exprParser.name());
                        if (lexer.token() == Token.COMMA) {
                            lexer.nextToken();
                            continue;
                        }
                        break;
                    }
                    accept(Token.RPAREN);
                    stmt.setPartitioning(clause);
                    
                    if (identifierEquals("PARTITIONS")) {
                        lexer.nextToken();
                        clause.setPartitionCount(this.exprParser.expr());
                    }
                } else {
                    throw new ParserException("TODO " + lexer.token() + " " + lexer.stringVal());
                }
            }

            break;
        }

        if (lexer.token() == (Token.ON)) {
            throw new ParserException("TODO");
        }

        if (lexer.token() == (Token.SELECT)) {
            SQLSelect query = new MySqlSelectParser(this.exprParser).select();
            stmt.setQuery(query);
        }

        return stmt;
    }

    @SuppressWarnings("unused")
    protected SQLTableConstaint parseConstraint() {
        SQLName name = null;
        if (lexer.token() == (Token.CONSTRAINT)) {
            lexer.nextToken();
        }

        if (lexer.token() == Token.IDENTIFIER) {
            name = this.exprParser.name();
        }

        if (lexer.token() == (Token.KEY)) {
            lexer.nextToken();

            MySqlKey key = new MySqlKey();

            if (identifierEquals("USING")) {
                lexer.nextToken();
                key.setIndexType(lexer.stringVal());
                lexer.nextToken();
            }

            accept(Token.LPAREN);
            for (;;) {
                key.getColumns().add(this.exprParser.expr());
                if (!(lexer.token() == (Token.COMMA))) {
                    break;
                } else {
                    lexer.nextToken();
                }
            }
            accept(Token.RPAREN);

            return key;
        }

        if (lexer.token() == (Token.PRIMARY)) {
            return (SQLTableConstaint) this.exprParser.parsePrimaryKey();
        }

        throw new ParserException("TODO");
    }

  
}