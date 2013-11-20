/* 
 * GameState.g defines the GameState grammar and generates the lexer and
 * parser.
 *
 */
grammar GameState;
options {
    // output AST for use in StrategyEval evaluator.
    output=AST;
    ASTLabelType=CommonTree;
}

tokens {
    GAME_STATE;
    CYCLE;
    PLAYER;
    GAME_MAP;
    RESOURCE;
    CELLS;
    REGION;
    CENTER;
    CONNECTIONS;
    PASSAGE;
    PASSAGE_NODE;
    UNITS;
    UNIT;
}

@header {
package orst.stratagusai.stratplan;
}

@lexer::header {
package orst.stratagusai.stratplan;
}

@members {
    public static void main(String[] args) throws Exception {
        GameStateLexer lex = new GameStateLexer(new ANTLRFileStream(args[0]));
       	CommonTokenStream tokens = new CommonTokenStream(lex);

        GameStateParser parser = new GameStateParser(tokens);
 
        try {
            parser.game_state();
        } catch (RecognitionException e)  {
            e.printStackTrace();
        }
    }
}

/*------------------------------------------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

/* top level */

game_state
    : '(' ':GameState' ':cycle' INT player+ map units ')' -> ^(GAME_STATE CYCLE INT player+ map units);

/* (:player :id 0 :gold 2000 :wood 1000 :oil 1000 :supply 0 :demand 5) */
player
    : '(' ':player' ':id' INT pair+ ')' -> ^(PLAYER INT pair+);

map
    : '(' ':GameMap' cells? region+ cnx? ')' -> ^(GAME_MAP cells? region+ cnx?)
    | '(' ':GameMap' ':resource' STRING ')' -> ^(GAME_MAP RESOURCE STRING);

cells
    : '(' ':cells' map_row+ ')' -> ^(CELLS map_row+);

map_row
    : STRING;

region
    : '(' ':Region' ':id' INT ':center' location chokepoint? rect+  ')' ->
            ^(REGION INT location chokepoint? rect+);

chokepoint
    : ':chokepoint';

location
    : '(' INT INT ')' -> ^(INT INT);
rect
    : '(' INT INT INT INT ')' -> ^(INT INT INT INT);

cnx
    : '(' ':connections' pnode+ passage+ ')';
/* ANTLR error in remap rule -> ^(CONNECTIONS ... ); */

passage
/* (:Passage :regionNode 2 :passageNode 1) */
    : '(' ':Passage' pair pair ')' -> ^(PASSAGE pair pair);

pnode
/* (:PassageNode :id 1 (x y)) */
    : '(' ':PassageNode' ':id' INT location ')' -> ^(PASSAGE_NODE INT location);

units
    : '(' ':units' unit* ')' -> ^(UNITS unit*);

unit
    : '(' ':Unit' pair+ ')' -> ^(UNIT pair+);

pair
    : LABEL (INT|NAME);

/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

LABEL   : ':'NAME;

NAME	:('a'..'z'|'A'..'Z'|'_')(options{greedy=true;}:	'-'|'a'..'z'|'A'..'Z'|'_'|'0'..'9')*;

/*
 A quote delimited character string.  Greedy is turned off so that .* doesn't
 consume the terminating quote.
*/
STRING : '\'' ( options {greedy=false;} : . )* '\'';


INT	: '-'?(DIGIT)+ ;

WHITESPACE : ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+ 	{ skip(); } ;

fragment DIGIT	: '0'..'9' ;

LINE_COMMENT
    : '#' ~('\n'|'\r')* '\r'? '\n' {skip();}
    ;
