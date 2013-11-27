// This file is part of the WhileLang Compiler (wlc).
//
// The WhileLang Compiler is free software; you can redistribute
// it and/or modify it under the terms of the GNU General Public
// License as published by the Free Software Foundation; either
// version 3 of the License, or (at your option) any later version.
//
// The WhileLang Compiler is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
// PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public
// License along with the WhileLang Compiler. If not, see
// <http://www.gnu.org/licenses/>
//
// Copyright 2013, David James Pearce.

package wyscript.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import wyscript.util.SyntaxError;

/**
 * Responsible for turning a stream of characters into a sequence of tokens.
 * 
 * @author Daivd J. Pearce
 * 
 */
public class Lexer {

	private String filename;
	private StringBuffer input;
	private int pos;

	public Lexer(String filename) throws IOException {
		this(new InputStreamReader(new FileInputStream(filename), "UTF8"));
		this.filename = filename;
	}

	public Lexer(InputStream instream) throws IOException {
		this(new InputStreamReader(instream, "UTF8"));
	}

	public Lexer(Reader reader) throws IOException {
		BufferedReader in = new BufferedReader(reader);

		StringBuffer text = new StringBuffer();
		String tmp;
		while ((tmp = in.readLine()) != null) {
			text.append(tmp);
			text.append("\n");
		}

		input = text;
	}

	/**
	 * Scan all characters from the input stream and generate a corresponding
	 * list of tokens, whilst discarding all whitespace and comments.
	 * 
	 * @return
	 */
	public List<Token> scan() {
		ArrayList<Token> tokens = new ArrayList<Token>();
		pos = 0;

		while (pos < input.length()) {
			char c = input.charAt(pos);

			if (Character.isDigit(c)) {
				tokens.add(scanNumericConstant());
			} else if (c == '"') {
				tokens.add(scanStringConstant());
			} else if (c == '\'') {
				tokens.add(scanCharacterConstant());
			} else if (isOperatorStart(c)) {
				tokens.add(scanOperator());
			} else if (Character.isJavaIdentifierStart(c)) {
				tokens.add(scanIdentifier());
			} else if(Character.isWhitespace(c)) {
				scanWhiteSpace(tokens);
			} else {
				syntaxError("syntax error");
			}
		}

		return tokens;
	}

	/**
	 * Scan a numeric constant. That is a sequence of digits which gives either
	 * an integer constant, or a real constant (if it includes a dot).
	 * 
	 * @return
	 */
	public Token scanNumericConstant() {
		int start = pos;
		while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
			pos = pos + 1;
		}
		if (pos < input.length() && input.charAt(pos) == '.') {
			pos = pos + 1;
			if (pos < input.length() && input.charAt(pos) == '.') {
				// this is case for range e.g. 0..1
				pos = pos - 1;
				return new Token(Token.Kind.IntValue, input.substring(start,
						pos), start);
			}
			while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
				pos = pos + 1;
			}
			return new Token(Token.Kind.RealValue, input.substring(start, pos),
					start);
		} else {
			return new Token(Token.Kind.IntValue, input.substring(start, pos),
					start);
		}
	}

	/**
	 * Scan a character constant, such as e.g. 'c'. Observe that care must be
	 * taken to properly handle escape codes. For example, '\n' is a single
	 * character constant which is made up from two characters in the input
	 * string.
	 * 
	 * @return
	 */
	public Token scanCharacterConstant() {
		int start = pos;
		pos++;
		char c = input.charAt(pos++);
		if (c == '\\') {
			// escape code
			switch (input.charAt(pos++)) {
			case 't':
				c = '\t';
				break;
			case 'n':
				c = '\n';
				break;
			default:
				syntaxError("unrecognised escape character", pos);
			}
		}
		if (input.charAt(pos) != '\'') {
			syntaxError("unexpected end-of-character", pos);
		}
		pos = pos + 1;
		return new Token(Token.Kind.CharValue, input.substring(start, pos),
				start, c);
	}
	
	public Token scanStringConstant() {
		int start = pos;
		pos++;
		while (pos < input.length()) {
			char c = input.charAt(pos);
			if (c == '"') {
				String v = input.substring(start, ++pos);
				return new Token(Token.Kind.String, v, start, parseString(v));
			}
			pos = pos + 1;
		}
		syntaxError("unexpected end-of-string", pos - 1);
		return null;
	}

	protected String parseString(String v) {
		/*
		 * Parsing a string requires several steps to be taken. First, we need
		 * to strip quotes from the ends of the string.
		 */
		int start = pos;
		v = v.substring(1, v.length() - 1);
		// Second, step through the string and replace escaped characters
		for (int i = 0; i < v.length(); i++) {
			if (v.charAt(i) == '\\') {
				if (v.length() <= i + 1) {
					syntaxError("unexpected end-of-string", start + i);
				} else {
					char replace = 0;
					int len = 2;
					switch (v.charAt(i + 1)) {
					case 'b':
						replace = '\b';
						break;
					case 't':
						replace = '\t';
						break;
					case 'n':
						replace = '\n';
						break;
					case 'f':
						replace = '\f';
						break;
					case 'r':
						replace = '\r';
						break;
					case '"':
						replace = '\"';
						break;
					case '\'':
						replace = '\'';
						break;
					case '\\':
						replace = '\\';
						break;
					case 'u':
						len = 6; // unicode escapes are six digits long,
						// including "slash u"
						String unicode = v.substring(i + 2, i + 6);
						replace = (char) Integer.parseInt(unicode, 16); // unicode
						break;
					default:
						syntaxError("unknown escape character", start + i);
					}
					v = v.substring(0, i) + replace + v.substring(i + len);
				}
			}
		}
		return v;
	}
	
	static final char[] opStarts = { ',', '(', ')', '[', ']', '{', '}', '+',
			'-', '*', '/', '%', '!', '?', '=', '<', '>', ':', ';', '&', '|',
			'.', '~' };

	public boolean isOperatorStart(char c) {
		for (char o : opStarts) {
			if (c == o) {
				return true;
			}
		}
		return false;
	}

	public Token scanOperator() {
		char c = input.charAt(pos);

		if (c == '.') {			
			return new Token(Token.Kind.Dot,".",pos++);
		} else if (c == ',') {
			return new Token(Token.Kind.Comma,",",pos++);
		} else if (c == ';') {
			return new Token(Token.Kind.SemiColon,";",pos++);
		} else if (c == ':') {
			return new Token(Token.Kind.Colon,":",pos++);
		} else if (c == '|') {
			return new Token(Token.Kind.Bar,"|",pos++);
		} else if (c == '(') {
			return new Token(Token.Kind.LeftBrace,"(",pos++);
		} else if (c == ')') {
			return new Token(Token.Kind.RightBrace,")",pos++);
		} else if (c == '[') {
			return new Token(Token.Kind.LeftSquare,"[",pos++);
		} else if (c == ']') {
			return new Token(Token.Kind.RightSquare,"]",pos++);
		} else if (c == '{') {
			return new Token(Token.Kind.LeftCurly,"{",pos++);
		} else if (c == '}') {
			return new Token(Token.Kind.RightCurly,"}",pos++);
		} else if (c == '+') {
			if((pos+1) < input.length() && input.charAt(pos+1) == '+') {
				pos = pos + 2;
				return new Token(Token.Kind.PlusPlus,"++",pos);
			} else {
				return new Token(Token.Kind.Plus,"+",pos++);
			}
		} else if (c == '-') {			
			return new Token(Token.Kind.Minus,"-",pos++);
		} else if (c == '*') {
			return new Token(Token.Kind.Star,"*",pos++);
		} else if (c == '&' && (pos + 1) < input.length()
				&& input.charAt(pos + 1) == '&') {
			pos += 2;
			return new Token(Token.Kind.LogicalAnd,"&&", pos - 2);
		} else if (c == '/') {			
			return new Token(Token.Kind.RightSlash,"/",pos++);
		} else if (c == '%') {			
			return new Token(Token.Kind.Percent,"%",pos++);
		} else if (c == '!') {
			if ((pos + 1) < input.length() && input.charAt(pos + 1) == '=') {
				pos += 2;
				return new Token(Token.Kind.NotEquals, "!=", pos - 2);
			} else {
				return new Token(Token.Kind.Shreak,"!",pos++);
			}
		} else if (c == '=') {
			if ((pos + 1) < input.length() && input.charAt(pos + 1) == '=') {
				pos += 2;
				return new Token(Token.Kind.EqualsEquals,"==",pos - 2);
			} else {
				return new Token(Token.Kind.Equals,"=",pos++);
			}
		} else if (c == '<') {
			if ((pos + 1) < input.length() && input.charAt(pos + 1) == '=') {
				pos += 2;
				return new Token(Token.Kind.LessEquals, "<=", pos - 2);
			} else {
				return new Token(Token.Kind.LeftAngle, "<", pos++);
			}
		} else if (c == '>') {
			if ((pos + 1) < input.length() && input.charAt(pos + 1) == '=') {
				pos += 2;
				return new Token(Token.Kind.GreaterEquals,">=", pos - 2);
			} else {
				return new Token(Token.Kind.RightAngle,">",pos++);
			}
		} 

		syntaxError("unknown operator encountered: " + c);
		return null;
	}
	
	public Token scanIdentifier() {
		int start = pos;
		while (pos < input.length()
				&& Character.isJavaIdentifierPart(input.charAt(pos))) {
			pos++;
		}
		String text = input.substring(start, pos);

		// now, check for keywords
		Token.Kind kind = keywords.get(text);
		if (kind == null) {
			// not a keyword, so just a regular identifier.
			kind = Token.Kind.Identifier;
		}
		return new Token(kind, text, start);
	}
	
	public void scanWhiteSpace(List<Token> tokens) {
		while (pos < input.length()
				&& Character.isWhitespace(input.charAt(pos))) {
			if (input.charAt(pos) == ' ' || input.charAt(pos) == '\t') {
				tokens.add(scanIndent());
			} else if (input.charAt(pos) == '\n') {
				tokens.add(new Token(Token.Kind.NewLine, input.substring(pos,
						pos + 1), pos));
				pos = pos + 1;
			} else if (input.charAt(pos) == '\r' && (pos + 1) < input.length()
					&& input.charAt(pos + 1) == '\n') {
				tokens.add(new Token(Token.Kind.NewLine, input.substring(pos,
						pos + 2), pos));
				pos = pos + 2;
			} else {
				syntaxError("unknown whitespace character encounterd: \""
						+ input.charAt(pos));
			}
		}
	}
	
	/**
	 * Scan one or more spaces or tab characters, combining them to form an
	 * "indent".
	 * 
	 * @return
	 */
	public Token scanIndent() {
		int start = pos;
		while (pos < input.length()
				&& (input.charAt(pos) == ' ' || input.charAt(pos) == '\t')) {
			pos++;
		}
		return new Token(Token.Kind.Indent, input.substring(start, pos), start);
	}
		
	/**
	 * Skip over any whitespace at the current index position in the input
	 * string.
	 * 
	 * @param tokens
	 */
	public void skipWhitespace(List<Token> tokens) {
		while (pos < input.length()
				&& (input.charAt(pos) == '\n' || input.charAt(pos) == '\t')) {
			pos++;
		}
	}

	/**
	 * Raise a syntax error with a given message at given index.
	 * 
	 * @param msg
	 *            --- message to raise.
	 * @param index
	 *            --- index position to associate the error with.
	 */
	private void syntaxError(String msg, int index) {
		throw new SyntaxError(msg, filename, index, index);
	}

	/**
	 * Raise a syntax error with a given message at the current index.
	 * 
	 * @param msg
	 * @param index
	 */
	private void syntaxError(String msg) {
		throw new SyntaxError(msg, filename, pos, pos);
	}

	/**
	 * A map from identifier strings to the corresponding token kind.
	 */
	public static final HashMap<String, Token.Kind> keywords = new HashMap<String, Token.Kind>() {
		{
			put("int", Token.Kind.Int);
			put("real", Token.Kind.Real);
			put("char", Token.Kind.Char);
			put("string", Token.Kind.String);
			put("bool", Token.Kind.Bool);
			put("if", Token.Kind.If);
			put("else", Token.Kind.Else);
			put("switch", Token.Kind.Switch);
			put("while", Token.Kind.While);
			put("for", Token.Kind.For);
			put("print", Token.Kind.Print);
			put("return", Token.Kind.Return);
			put("constant", Token.Kind.Constant);
			put("type", Token.Kind.Type);
		}
	};	
	
	/**
	 * The base class for all tokens.
	 * 
	 * @author David J. Pearce
	 * 
	 */
	public static class Token {

		public enum Kind {			
			Identifier,
			// Keywords
			True,
			False,
			Null,
			Void,
			Bool,
			Int,
			Real,
			Char,
			String,
			If,
			Switch,
			While,
			Else,
			Is,
			For,
			Debug,
			Print,
			Return,
			Constant,
			Type,			
			// Constants
			RealValue,
			IntValue,
			CharValue,
			StringValue,
			// Symbols
			Comma,
			SemiColon,
			Colon,
			Bar,
			LeftBrace,
			RightBrace,
			LeftSquare,
			RightSquare,
			LeftAngle,
			RightAngle,
			LeftCurly,
			RightCurly,
			PlusPlus,
			Plus,
			Minus,
			Star,
			Divide,
			LeftSlash,
			RightSlash,
			Percent,
			Shreak,
			Dot,
			Equals,
			EqualsEquals,
			NotEquals,
			LessEquals,
			GreaterEquals,
			LogicalAnd,
			LogicalOr,
			LogicalNot,
			// Other			
			NewLine,
			Indent			
		}
		
		public final Kind kind;
		public final String text;
		public final int start;

		public Token(Kind kind, String text, int pos) {
			this.kind = kind;
			this.text = text;
			this.start = pos;
		}

		public int end() {
			return start + text.length() - 1;
		}
	}	
}
