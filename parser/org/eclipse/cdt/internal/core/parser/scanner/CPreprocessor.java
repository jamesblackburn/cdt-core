/*******************************************************************************
 * Copyright (c) 2004, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM - Initial API and implementation
 *    Anton Leherbauer (Wind River Systems)
 *    Markus Schorn (Wind River Systems)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.parser.scanner;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.parser.IScannerExtensionConfiguration;
import org.eclipse.cdt.core.index.IIndexMacro;
import org.eclipse.cdt.core.parser.CodeReader;
import org.eclipse.cdt.core.parser.EndOfFileException;
import org.eclipse.cdt.core.parser.IExtendedScannerInfo;
import org.eclipse.cdt.core.parser.IMacro;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IPreprocessorDirective;
import org.eclipse.cdt.core.parser.IProblem;
import org.eclipse.cdt.core.parser.IScanner;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IToken;
import org.eclipse.cdt.core.parser.Keywords;
import org.eclipse.cdt.core.parser.OffsetLimitReachedException;
import org.eclipse.cdt.core.parser.ParseError;
import org.eclipse.cdt.core.parser.ParserLanguage;
import org.eclipse.cdt.core.parser.util.CharArrayIntMap;
import org.eclipse.cdt.core.parser.util.CharArrayObjectMap;
import org.eclipse.cdt.core.parser.util.CharArrayUtils;
import org.eclipse.cdt.internal.core.parser.scanner.ExpressionEvaluator.EvalException;
import org.eclipse.cdt.internal.core.parser.scanner.Lexer.LexerOptions;
import org.eclipse.cdt.internal.core.parser.scanner.MacroDefinitionParser.InvalidMacroDefinitionException;
import org.eclipse.cdt.internal.core.parser.scanner2.IIndexBasedCodeReaderFactory;
import org.eclipse.cdt.internal.core.parser.scanner2.ScannerUtility;

/**
 * C-Preprocessor providing tokens for the parsers. The class should not be used directly, rather than that 
 * you should be using the {@link IScanner} interface.
 * @since 5.0
 */
public class CPreprocessor implements ILexerLog, IScanner {
	public static final String PROP_VALUE = "CPreprocessor"; //$NON-NLS-1$

	public static final int tDEFINED= IToken.FIRST_RESERVED_PREPROCESSOR;
	public static final int tEXPANDED_IDENTIFIER= IToken.FIRST_RESERVED_PREPROCESSOR+1;
	public static final int tSCOPE_MARKER= IToken.FIRST_RESERVED_PREPROCESSOR+2;
	public static final int tSPACE= IToken.FIRST_RESERVED_PREPROCESSOR+3;
	public static final int tNOSPACE= IToken.FIRST_RESERVED_PREPROCESSOR+4;
	public static final int tMACRO_PARAMETER= IToken.FIRST_RESERVED_PREPROCESSOR+5;
	public static final int tEMPTY_TOKEN = IToken.FIRST_RESERVED_PREPROCESSOR+6;

    

	private static final int ORIGIN_PREPROCESSOR_DIRECTIVE = OffsetLimitReachedException.ORIGIN_PREPROCESSOR_DIRECTIVE;
	private static final int ORIGIN_INACTIVE_CODE = OffsetLimitReachedException.ORIGIN_INACTIVE_CODE;
//	private static final int ORIGIN_MACRO_EXPANSION = OffsetLimitReachedException.ORIGIN_MACRO_EXPANSION;
	
    private static final char[] EMPTY_CHAR_ARRAY = new char[0];
    private static final char[] ONE = "1".toCharArray(); //$NON-NLS-1$
    private static final String EMPTY_STRING = ""; //$NON-NLS-1$


    // standard built-ins
    private static final ObjectStyleMacro __cplusplus = new ObjectStyleMacro("__cplusplus".toCharArray(), ONE);   //$NON-NLS-1$
    private static final ObjectStyleMacro __STDC__ = new ObjectStyleMacro("__STDC__".toCharArray(), ONE);  //$NON-NLS-1$
    private static final ObjectStyleMacro __STDC_HOSTED__ = new ObjectStyleMacro("__STDC_HOSTED_".toCharArray(), ONE);  //$NON-NLS-1$
    private static final ObjectStyleMacro __STDC_VERSION__ = new ObjectStyleMacro("__STDC_VERSION_".toCharArray(), "199901L".toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$

	private interface IIncludeFileTester {
    	Object checkFile(String path, String fileName);
    }

    final private IIncludeFileTester createCodeReaderTester= new IIncludeFileTester() { 
    	public Object checkFile(String path, String fileName) {
    		return createReader(path, fileName);
    	}
    };
    
    final private IIncludeFileTester createPathTester= new IIncludeFileTester() { 
    	public Object checkFile(String path, String fileName) {
    		path= ScannerUtility.createReconciledPath(path, fileName);
    		if (new File(path).exists()) {
    			return path;
    		}
    		return null;
    	}
    };

    // standard built-ins
    final private DynamicStyleMacro __FILE__= new DynamicStyleMacro("__FILE__".toCharArray()) { //$NON-NLS-1$
        public Token execute() {
            StringBuffer buffer = new StringBuffer("\""); //$NON-NLS-1$
            buffer.append(getCurrentFilename());
            buffer.append('\"');
            return new TokenWithImage(IToken.tSTRING, null, 0, 0, buffer.toString().toCharArray());
        }
    };
    final private DynamicStyleMacro __DATE__= new DynamicStyleMacro("__DATE__".toCharArray()) { //$NON-NLS-1$
        final private void append(StringBuffer buffer, int value) {
            if (value < 10)
                buffer.append("0"); //$NON-NLS-1$
            buffer.append(value);
        }

        public Token execute() {
            StringBuffer buffer = new StringBuffer("\""); //$NON-NLS-1$
            Calendar cal = Calendar.getInstance();
            buffer.append(cal.get(Calendar.MONTH));
            buffer.append(" "); //$NON-NLS-1$
            append(buffer, cal.get(Calendar.DAY_OF_MONTH));
            buffer.append(" "); //$NON-NLS-1$
            buffer.append(cal.get(Calendar.YEAR));
            buffer.append("\""); //$NON-NLS-1$
            return new TokenWithImage(IToken.tSTRING, null, 0, 0, buffer.toString().toCharArray());
        }
    };

    final private DynamicStyleMacro __TIME__ = new DynamicStyleMacro("__TIME__".toCharArray()) { //$NON-NLS-1$
        final private void append(StringBuffer buffer, int value) {
            if (value < 10)
                buffer.append("0"); //$NON-NLS-1$
            buffer.append(value);
        }

        public Token execute() {
            StringBuffer buffer = new StringBuffer("\""); //$NON-NLS-1$
            Calendar cal = Calendar.getInstance();
            append(buffer, cal.get(Calendar.HOUR));
            buffer.append(":"); //$NON-NLS-1$
            append(buffer, cal.get(Calendar.MINUTE));
            buffer.append(":"); //$NON-NLS-1$
            append(buffer, cal.get(Calendar.SECOND));
            buffer.append("\""); //$NON-NLS-1$
            return new TokenWithImage(IToken.tSTRING, null, 0, 0, buffer.toString().toCharArray());
        }
    };

    final private DynamicStyleMacro __LINE__ = new DynamicStyleMacro("__LINE__".toCharArray()) { //$NON-NLS-1$
        public Token execute() {
            int lineNumber= fLocationMap.getCurrentLineNumber(fCurrentContext.currentLexerToken().getOffset());
            return new TokenWithImage(IToken.tINTEGER, null, 0, 0, Long.toString(lineNumber).toCharArray());
        }
    };

    final private IParserLogService fLog;
    final private ICodeReaderFactory fCodeReaderFactory;
    private final ExpressionEvaluator fExpressionEvaluator;
	private final MacroDefinitionParser fMacroDefinitionParser;
	private final MacroExpander fMacroExpander;

    // configuration
    final private LexerOptions fLexOptions= new LexerOptions();
    final private char[] fAdditionalNumericLiteralSuffixes;
    final private CharArrayIntMap fKeywords;
    final private CharArrayIntMap fPPKeywords;
    final private String[] fIncludePaths;
    final private String[] fQuoteIncludePaths;
    private String[][] fPreIncludedFiles= null;

    private int fContentAssistLimit= -1;
	private boolean fHandledCompletion= false;

    // state information
    private final CharArrayObjectMap fMacroDictionary = new CharArrayObjectMap(512);
    private final LocationMap fLocationMap = new LocationMap();

    /** Set of already included files */
    private final HashSet fAllIncludedFiles= new HashSet();
    private int fTokenCount;

	private final Lexer fRootLexer;
	private final ScannerContext fRootContext;
	private ScannerContext fCurrentContext;

    private boolean isCancelled = false;

	private Token fPrefetchedTokens;
    private Token fLastToken;

    public CPreprocessor(CodeReader reader, IScannerInfo info, ParserLanguage language, IParserLogService log,
            IScannerExtensionConfiguration configuration, ICodeReaderFactory readerFactory) {
        fLog = log;
        fAdditionalNumericLiteralSuffixes= nonNull(configuration.supportAdditionalNumericLiteralSuffixes());
        fLexOptions.fSupportDollarInitializers= configuration.support$InIdentifiers();
        fLexOptions.fSupportMinAndMax = configuration.supportMinAndMaxOperators();
        fKeywords= new CharArrayIntMap(40, -1);
        fPPKeywords= new CharArrayIntMap(40, -1);
        configureKeywords(language, configuration);

    	fIncludePaths= info.getIncludePaths();
    	fQuoteIncludePaths= getQuoteIncludePath(info);
    	
        fExpressionEvaluator= new ExpressionEvaluator();
        fMacroDefinitionParser= new MacroDefinitionParser();
        fMacroExpander= new MacroExpander(this, fMacroDictionary, fLocationMap, fMacroDefinitionParser, fLexOptions);
        fCodeReaderFactory= readerFactory;

        setupMacroDictionary(configuration, info, language);		
                
        final String filePath= new String(reader.filename);
        fAllIncludedFiles.add(filePath);
        ILocationCtx ctx= fLocationMap.pushTranslationUnit(filePath, reader.buffer);	
        fRootLexer= new Lexer(reader.buffer, fLexOptions, this, this);
        fRootContext= fCurrentContext= new ScannerContext(ctx, null, fRootLexer);
        if (info instanceof IExtendedScannerInfo) {
        	final IExtendedScannerInfo einfo= (IExtendedScannerInfo) info;
        	fPreIncludedFiles= new String[][] {einfo.getMacroFiles(), einfo.getIncludeFiles()};
        }
    }
    
    public void setComputeImageLocations(boolean val) {
    	fLexOptions.fCreateImageLocations= val;
    }
    
	public void setContentAssistMode(int offset) {
		fContentAssistLimit= offset;
		fRootLexer.setContentAssistMode(offset);
	}

	private void configureKeywords(ParserLanguage language,	IScannerExtensionConfiguration configuration) {
		Keywords.addKeywordsPreprocessor(fPPKeywords);
		if (language == ParserLanguage.C) {
        	Keywords.addKeywordsC(fKeywords);
        }
        else {
        	Keywords.addKeywordsCpp(fKeywords);
        }
        CharArrayIntMap additionalKeywords= configuration.getAdditionalKeywords();
        if (additionalKeywords != null) {
        	fKeywords.putAll(additionalKeywords);
        }
        additionalKeywords= configuration.getAdditionalPreprocessorKeywords();
        if (additionalKeywords != null) {
        	fPPKeywords.putAll(additionalKeywords);
        }
	}

    protected String getCurrentFilename() {
		return fLocationMap.getCurrentFilePath();
	}

	private char[] nonNull(char[] array) {
		return array == null ? EMPTY_CHAR_ARRAY : array;
	}

    private String[] getQuoteIncludePath(IScannerInfo info) {
        if (info instanceof IExtendedScannerInfo) {
        	final IExtendedScannerInfo einfo= (IExtendedScannerInfo) info;
            final String[] qip= einfo.getLocalIncludePath();
            if (qip != null && qip.length > 0) {
            	final String[] result= new String[qip.length + fIncludePaths.length];
            	System.arraycopy(qip, 0, result, 0, qip.length);
            	System.arraycopy(fIncludePaths, 0, result, qip.length, fIncludePaths.length);
            	return result;
            }
        }
        return info.getIncludePaths();
	}

    private void setupMacroDictionary(IScannerExtensionConfiguration config, IScannerInfo info, ParserLanguage lang) {
    	// built in macros
        fMacroDictionary.put(__STDC__.getNameCharArray(), __STDC__);
        fMacroDictionary.put(__FILE__.getNameCharArray(), __FILE__);
        fMacroDictionary.put(__DATE__.getNameCharArray(), __DATE__);
        fMacroDictionary.put(__TIME__.getNameCharArray(), __TIME__);
        fMacroDictionary.put(__LINE__.getNameCharArray(), __LINE__);

        if (lang == ParserLanguage.CPP)
            fMacroDictionary.put(__cplusplus.getNameCharArray(), __cplusplus);
        else {
            fMacroDictionary.put(__STDC_HOSTED__.getNameCharArray(), __STDC_HOSTED__);
            fMacroDictionary.put(__STDC_VERSION__.getNameCharArray(), __STDC_VERSION__);
        }

        CharArrayObjectMap toAdd = config.getAdditionalMacros();
        for (int i = 0; i < toAdd.size(); ++i) {
        	addDefinition((IMacro) toAdd.getAt(i));
        }
        
        // macros provided on command-line (-D)
        final boolean initEmptyMacros= config.initializeMacroValuesTo1();
        final Map macroDict= info.getDefinedSymbols();
        if (macroDict != null) {
        	for (Iterator iterator = macroDict.entrySet().iterator(); iterator.hasNext();) {
				final Map.Entry entry = (Map.Entry) iterator.next();
				final String key= (String) entry.getKey();
				final String value= ((String) entry.getValue()).trim();
				if (initEmptyMacros && value.length() == 0) {
					addMacroDefinition(key.toCharArray(), ONE);
				}
				else {
					addMacroDefinition(key.toCharArray(), value.toCharArray());
                }
            }
        }
        
        Object[] predefined= fMacroDictionary.valueArray();
        for (int i = 0; i < predefined.length; i++) {
        	fLocationMap.registerPredefinedMacro((PreprocessorMacro) predefined[i]);
		}
    }

    private void handlePreIncludedFiles() {
    	final String[] imacro= fPreIncludedFiles[0];
    	if (imacro != null && imacro.length > 0) {
    		final char[] buffer= createSyntheticFile(imacro);
    		ILocationCtx ctx= fLocationMap.pushPreInclusion(buffer, 0, true);
    		fCurrentContext= new ScannerContext(ctx, fCurrentContext, new Lexer(buffer, fLexOptions, this, this));
    		ScannerContext preCtx= fCurrentContext;
    		try {
				while(internalFetchToken(true, false, true, preCtx).getType() != IToken.tEND_OF_INPUT) {
				// just eat the tokens
				}
            	final ILocationCtx locationCtx = fCurrentContext.getLocationCtx();
            	fLocationMap.popContext(locationCtx);
        		fCurrentContext= fCurrentContext.getParent();
        		assert fCurrentContext == fRootContext;
			} catch (OffsetLimitReachedException e) {
			}
    	}
    	final String[] include= fPreIncludedFiles[1];
    	if (include != null && include.length > 0) {
    		final char[] buffer= createSyntheticFile(include);
    		ILocationCtx ctx= fLocationMap.pushPreInclusion(buffer, 0, false);
    		fCurrentContext= new ScannerContext(ctx, fCurrentContext, new Lexer(buffer, fLexOptions, this, this));
    	}
    	fPreIncludedFiles= null;
    } 
    
	private char[] createSyntheticFile(String[] files) {
		int totalLength= 0;
    	final char[] instruction= "#include <".toCharArray(); //$NON-NLS-1$
    	for (int i = 0; i < files.length; i++) {
    		totalLength+= instruction.length + 2 + files[i].length();
    	}
    	final char[] buffer= new char[totalLength];
    	int pos= 0;
    	for (int i = 0; i < files.length; i++) {
    		final char[] fileName= files[i].toCharArray();
    		System.arraycopy(instruction, 0, buffer, pos, instruction.length);
    		pos+= instruction.length;
    		System.arraycopy(fileName, 0, buffer, pos, fileName.length);
    		pos+= fileName.length;
    		buffer[pos++]= '>';
    		buffer[pos++]= '\n';
    	}
    	return buffer;
	}
    
    public PreprocessorMacro addMacroDefinition(char[] key, char[] value) {
     	final Lexer lex= new Lexer(key, fLexOptions, ILexerLog.NULL, null);
    	try {
    		PreprocessorMacro result= fMacroDefinitionParser.parseMacroDefinition(lex, ILexerLog.NULL, value);
    		fLocationMap.registerPredefinedMacro(result);
	    	fMacroDictionary.put(result.getNameCharArray(), result);
	    	return result;
    	}
    	catch (Exception e) {
    		fLog.traceLog("Invalid macro definition: '" + String.valueOf(key) + "'");     //$NON-NLS-1$//$NON-NLS-2$
    		return null;
    	}
    }
  
    public int getCount() {
        return fTokenCount;
    }

    public Map getDefinitions() {
        final CharArrayObjectMap objMap= fMacroDictionary;
        int size = objMap.size();
        Map hashMap = new HashMap(size);
        for (int i = 0; i < size; i++) {
            hashMap.put(String.valueOf(objMap.keyAt(i)), objMap.getAt(i));
        }

        return hashMap;
    }

    public String[] getIncludePaths() {
        return fIncludePaths;
    }

    public boolean isOnTopContext() {
    	return fCurrentContext == fRootContext;
    }

    public synchronized void cancel() {
    	isCancelled= true;
    }

    /**
     * Returns the next token from the preprocessor without concatenating string literals.
     */
    private Token fetchToken() throws OffsetLimitReachedException {
    	if (fPreIncludedFiles != null) {
    		handlePreIncludedFiles();
    	}
    	Token t= fPrefetchedTokens;
    	if (t != null) {
    		fPrefetchedTokens= (Token) t.getNext();
    		t.setNext(null);
    		return t;
    	}
    	
    	try {
			t= internalFetchToken(true, false, true, fRootContext);
		} catch (OffsetLimitReachedException e) {
			fHandledCompletion= true;
			throw e;
		}
    	final int offset= fLocationMap.getSequenceNumberForOffset(t.getOffset());
		final int endOffset= fLocationMap.getSequenceNumberForOffset(t.getEndOffset());
		t.setOffset(offset, endOffset);
		t.setNext(null);
    	return t;
    }
    
    private void pushbackToken(Token t) {
    	t.setNext(fPrefetchedTokens);
    	fPrefetchedTokens= t;
    }
    
    /**
     * Returns next token for the parser. String literals are not concatenated. When 
     * the end is reached tokens with type {@link IToken#tEND_OF_INPUT}.
     * @throws OffsetLimitReachedException see {@link Lexer}.
     */
    public IToken nextTokenRaw() throws OffsetLimitReachedException {
        if (isCancelled) {
            throw new ParseError(ParseError.ParseErrorKind.TIMEOUT_OR_CANCELLED);
        }
        
    	Token t1= fetchToken();
    	switch (t1.getType()) {
    	case IToken.tCOMPLETION:
    		fHandledCompletion= true;
    		break;
    	case IToken.tEND_OF_INPUT:
    		if (fContentAssistLimit >= 0) {
        		int useType= fHandledCompletion ? IToken.tEOC : IToken.tCOMPLETION; 
    			int sequenceNumber= fLocationMap.getSequenceNumberForOffset(fContentAssistLimit);
    			t1= new Token(useType, null, sequenceNumber, sequenceNumber);
    			fHandledCompletion= true;
    		}
    		break;
    	}
    	if (fLastToken != null) {
    		fLastToken.setNext(t1);
    	}
    	fLastToken= t1;
    	return t1;
    }

    /**
     * Returns next token for the parser. String literals are concatenated.
     * @throws EndOfFileException when the end of the translation unit has been reached.
     * @throws OffsetLimitReachedException see {@link Lexer}.
     */
    public IToken nextToken() throws EndOfFileException {
        if (isCancelled) {
            throw new ParseError(ParseError.ParseErrorKind.TIMEOUT_OR_CANCELLED);
        }
        
    	Token t1= fetchToken();
    	
    	final int tt1= t1.getType();
    	switch(tt1) {
    	case IToken.tCOMPLETION:
    		fHandledCompletion= true;
    		break;
    		
    	case IToken.tEND_OF_INPUT:
    		if (fContentAssistLimit < 0) {
    			throw new EndOfFileException();
    		}
    		int useType= fHandledCompletion ? IToken.tEOC : IToken.tCOMPLETION; 
    		int sequenceNumber= fLocationMap.getSequenceNumberForOffset(fContentAssistLimit);
    		t1= new Token(useType, null, sequenceNumber, sequenceNumber);
    		fHandledCompletion= true;
    		break;
    		
    	case IToken.tSTRING:
    	case IToken.tLSTRING:
    		boolean isWide= tt1 == IToken.tLSTRING;
    		Token t2;
    		StringBuffer buf= null;
    		int endOffset= 0;
    		loop: while(true) {
    			t2= fetchToken();
    			final int tt2= t2.getType();
    			switch(tt2) {
    			case IToken.tLSTRING:
    				isWide= true;
    				// no break;
    			case IToken.tSTRING:
    				if (buf == null) {
    					buf= new StringBuffer();
    					appendStringContent(buf, t1);
    				}
    				appendStringContent(buf, t2);
    				endOffset= t2.getEndOffset();
    				continue loop;
    				
    			default:
    				break loop;
    			}
    		}
    		pushbackToken(t2);
    		if (buf != null) {
    			char[] image= new char[buf.length() + (isWide ? 3 : 2)];
    			int off= -1;
    			if (isWide) {
    				image[++off]= 'L';
    			}
    			image[++off]= '"';
    			buf.getChars(0, buf.length(), image, ++off);
    			image[image.length-1]= '"';
    			t1= new TokenWithImage((isWide ? IToken.tLSTRING : IToken.tSTRING), null, t1.getOffset(), endOffset, image);
    		}
    	}

    	if (fLastToken != null) {
    		fLastToken.setNext(t1);
    	}
    	fLastToken= t1;
    	return t1;
    }

    private void appendStringContent(StringBuffer buf, Token t1) {
    	final char[] image= t1.getCharImage();
    	final int start= image[0]=='"' ? 1 : 2;
    	buf.append(image, start, image.length-start-1);
	}

	Token internalFetchToken(final boolean expandMacros, final boolean stopAtNewline, 
			final boolean checkNumbers, final ScannerContext uptoEndOfCtx) throws OffsetLimitReachedException {
        ++fTokenCount;
        Token ppToken= fCurrentContext.currentLexerToken();
        while(true) {
			switch(ppToken.getType()) {
        	case Lexer.tBEFORE_INPUT:
    			ppToken= fCurrentContext.nextPPToken();
        		continue;
        		
        	case Lexer.tNEWLINE:
        		if (stopAtNewline) {
        			return ppToken;
        		}
        		ppToken= fCurrentContext.nextPPToken();
        		continue;

        	case Lexer.tOTHER_CHARACTER:
        		handleProblem(IProblem.SCANNER_BAD_CHARACTER, ppToken.getCharImage(), 
        				ppToken.getOffset(), ppToken.getEndOffset());
        		ppToken= fCurrentContext.nextPPToken();
        		continue;
        		
        	case IToken.tEND_OF_INPUT:
        		if (fCurrentContext == uptoEndOfCtx || uptoEndOfCtx == null) {
        			return ppToken;
        		}
            	final ILocationCtx locationCtx = fCurrentContext.getLocationCtx();
            	fLocationMap.popContext(locationCtx);
        		fCurrentContext= fCurrentContext.getParent();
        		assert fCurrentContext != null;
            	
        		ppToken= fCurrentContext.currentLexerToken();
        		continue;

            case IToken.tPOUND: 
               	{
               		final Lexer lexer= fCurrentContext.getLexer();
               		if (lexer != null && lexer.currentTokenIsFirstOnLine()) {
               			executeDirective(lexer, ppToken.getOffset());
               			ppToken= fCurrentContext.currentLexerToken();
               			continue;
               		}
               		break;
               	}
        	
        	case IToken.tIDENTIFIER:
        		fCurrentContext.nextPPToken(); // consume the identifier
        		if (expandMacros) {
        			final Lexer lexer= fCurrentContext.getLexer();
        			if (lexer != null && expandMacro(ppToken, lexer, stopAtNewline)) {
        				ppToken= fCurrentContext.currentLexerToken();
        				continue;
        			}

        			final char[] name= ppToken.getCharImage();
        			int tokenType = fKeywords.get(name);
        			if (tokenType != fKeywords.undefined) {
        				ppToken.setType(tokenType);
        			}
        		}
            	return ppToken;
        		
        	case IToken.tINTEGER:
        		if (checkNumbers) {
        			checkNumber(ppToken, false);
        		}
        		break;

        	case IToken.tFLOATINGPT:
        		if (checkNumbers) {
        			checkNumber(ppToken, true);
        		}
        		break;
        	}
			fCurrentContext.nextPPToken();
        	return ppToken;
        }
    }

    private void checkNumber(Token number, final boolean isFloat) {
        final char[] image= number.getCharImage();
        boolean hasExponent = false;

        boolean isHex = false;
        boolean isOctal = false;
        boolean hasDot= false;

        int pos= 0;
        if (image.length > 1) {
        	if (image[0] == '0') {
        		switch (image[++pos]) {
        		case 'x':
        		case 'X':
        			isHex = true;
        			++pos;
        			break;
        		case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7':
        			isOctal = true;
        			++pos;
        			break;
        		case '8': case '9':
        			handleProblem(IProblem.SCANNER_BAD_OCTAL_FORMAT, image, number.getOffset(), number.getEndOffset());
        			return;
        		}
        	}
        }

        loop: for (; pos < image.length; pos++) {
            switch (image[pos]) {
            // octal digits
            case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': 
            	continue;
            	
            // decimal digits
            case '8': case '9': 
            	if (isOctal) {
        			handleProblem(IProblem.SCANNER_BAD_OCTAL_FORMAT, image, number.getOffset(), number.getEndOffset());
        			return;
            	}
                continue;
                
            // hex digits
            case 'a': case 'A': case 'b': case 'B': case 'c': case 'C': case 'd': case 'D': case 'f': case 'F':
                if (isHex && !hasExponent) {
                    continue;
                }
                break loop;

            case '.':
                if (hasDot) {
                    handleProblem(IProblem.SCANNER_BAD_FLOATING_POINT, image, number.getOffset(), number.getEndOffset());
                    return;
                }
                hasDot= true;
                continue;

            // check for exponent or hex digit
            case 'E': case 'e':
                if (isHex && !hasExponent) {
                    continue;
                }
                if (isFloat && !isHex && !hasExponent && pos+1 <= image.length) {
                	switch (image[pos+1]) {
                	case '+': case '-':
                	case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                		hasExponent = true;
                		++pos;
                		continue;
                	}
                }
                break loop;

            // check for hex float exponent
            case 'p': case 'P':
                if (isFloat && isHex && !hasExponent && pos+1 >= image.length) {
                	switch (image[pos+1]) {
                	case '+': case '-':
                	case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                		hasExponent = true;
                		++pos;
                		continue;
                	}
                }
                break loop;

            default:
            	break loop;
            }
        }
        
        // check the suffix
        loop: for (; pos < image.length; pos++) {
        	final char c= image[pos];
            switch (c) {
            case 'u': case 'U': case 'L': case 'l':
            	continue;
            case 'f': case 'F':
            	if (isFloat) {
            		continue loop;
            	}
            }
            for (int i=0; i<fAdditionalNumericLiteralSuffixes.length; i++) {
				if (fAdditionalNumericLiteralSuffixes[i] == c) {
					continue loop;
				}
			}
            if (isFloat) {
            	handleProblem(IProblem.SCANNER_BAD_FLOATING_POINT, image, number.getOffset(), number.getEndOffset());
            }
            else if (isHex) {
            	handleProblem(IProblem.SCANNER_BAD_HEX_FORMAT, image, number.getOffset(), number.getEndOffset());
            }
            else if (isOctal) {
            	handleProblem(IProblem.SCANNER_BAD_OCTAL_FORMAT, image, number.getOffset(), number.getEndOffset());
            }
            else {
            	handleProblem(IProblem.SCANNER_BAD_DECIMAL_FORMAT, image, number.getOffset(), number.getEndOffset());
            }
            return;
        }
    }

    private CodeReader findInclusion(final String filename, final boolean quoteInclude, 
    		final boolean includeNext, final File currentDir) {
    	return (CodeReader) findInclusion(filename, quoteInclude, includeNext, currentDir, createCodeReaderTester);
    }

    private Object findInclusion(final String filename, final boolean quoteInclude, 
    		final boolean includeNext, final File currentDirectory, final IIncludeFileTester tester) {
        Object reader = null;
		// filename is an absolute path or it is a Linux absolute path on a windows machine
		if (new File(filename).isAbsolute() || filename.startsWith("/")) {  //$NON-NLS-1$
		    return tester.checkFile( EMPTY_STRING, filename );
		}
                
        if (currentDirectory != null && quoteInclude && !includeNext) {
            // Check to see if we find a match in the current directory
        	String absolutePath = currentDirectory.getAbsolutePath();
        	reader = tester.checkFile(absolutePath, filename);
        	if (reader != null) {
        		return reader;
        	}
        }
        
        // if we're not include_next, then we are looking for the first occurrence of 
        // the file, otherwise, we ignore all the paths before the current directory
        String[] includePathsToUse = quoteInclude ? fQuoteIncludePaths : fIncludePaths;
        if (includePathsToUse != null ) {
            int startpos = 0;
            if (includeNext && currentDirectory != null) {
                startpos = findIncludePos(includePathsToUse, currentDirectory) + 1;
            }
            for (int i = startpos; i < includePathsToUse.length; ++i) {
                reader = tester.checkFile(includePathsToUse[i], filename);
                if (reader != null) {
                	return reader;
                }
            }
        }
        return null;
    }

    private int findIncludePos(String[] paths, File currentDirectory) {
        for (int i = 0; i < paths.length; ++i)
            try {
                String path = new File(paths[i]).getCanonicalPath();
                String parent = currentDirectory.getCanonicalPath();
                if (path.equals(parent))
                    return i;
            } catch (IOException e) {
            }

        return -1;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer("Scanner @ file:");  //$NON-NLS-1$
        buffer.append(fCurrentContext.toString());
        buffer.append(" line: ");  //$NON-NLS-1$
        buffer.append(fLocationMap.getCurrentLineNumber(fCurrentContext.currentLexerToken().getOffset()));
        return buffer.toString();
    }
	
    
    public void addMacroDefinition(IIndexMacro macro) {
    	try {
    		PreprocessorMacro result= fMacroDefinitionParser.parseMacroDefinition(macro.getName(), macro.getParameterList(), macro.getExpansion());
    		final IASTFileLocation loc= macro.getFileLocation();
    		fLocationMap.registerMacroFromIndex(result, loc, -1);
	    	fMacroDictionary.put(result.getNameCharArray(), result);
    	}
    	catch (Exception e) {
    		fLog.traceLog("Invalid macro definition: '" + String.valueOf(macro.getName()) + "'");     //$NON-NLS-1$//$NON-NLS-2$
    	}
    }
    
    public ILocationResolver getLocationMap() {
    	return fLocationMap;
    }

	public void handleComment(boolean isBlockComment, int offset, int endOffset) {
		fLocationMap.encounteredComment(offset, endOffset, isBlockComment);
	}

    public void handleProblem(int id,  char[] arg, int offset, int endOffset) {
        fLocationMap.encounterProblem(id, arg, offset, endOffset);
    }

    private CodeReader createReader(String path, String fileName){
        String finalPath = ScannerUtility.createReconciledPath(path, fileName);
        CodeReader reader = fCodeReaderFactory.createCodeReaderForInclusion(this, finalPath);
        return reader;
    }
    
	
    /**
     * Assumes that the pound token has not yet been consumed
     * @param ppdCtx 
     * @param startOffset offset in current file
     * @since 5.0
     */
    private void executeDirective(final Lexer lexer, final int startOffset) throws OffsetLimitReachedException {
    	final Token ident= lexer.nextToken();
    	switch (ident.getType()) {
    	case IToken.tCOMPLETION:
    		lexer.nextToken();
    		Token completionToken= new TokenWithImage(ident.getType(), null, 
    				startOffset, ident.getEndOffset(), ("#" + ident.getImage()).toCharArray()); //$NON-NLS-1$
    		throw new OffsetLimitReachedException(ORIGIN_PREPROCESSOR_DIRECTIVE, completionToken);
    		
    	case Lexer.tNEWLINE:
    		return;

    	case IToken.tEND_OF_INPUT:
    	case IToken.tINTEGER:
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		return;

    	case IToken.tIDENTIFIER:
    		break;

    	default:
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		handleProblem(IProblem.PREPROCESSOR_INVALID_DIRECTIVE, ident.getCharImage(), startOffset, lexer.getLastEndOffset());
    		return;
    	}

    	// we have an identifier
    	final char[] name = ident.getCharImage();
    	final int type = fPPKeywords.get(name);
    	int condEndOffset;
    	switch (type) {
    	case IPreprocessorDirective.ppImport:
    	case IPreprocessorDirective.ppInclude:
    		executeInclude(lexer, startOffset, false, true);
    		break;
    	case IPreprocessorDirective.ppInclude_next:
    		executeInclude(lexer, startOffset, true, true);
    		break;
    	case IPreprocessorDirective.ppDefine:
    		executeDefine(lexer, startOffset);
    		break;
    	case IPreprocessorDirective.ppUndef:
    		executeUndefine(lexer, startOffset);
    		break;
    	case IPreprocessorDirective.ppIfdef:
    		executeIfdef(lexer, startOffset, true);
    		break;
    	case IPreprocessorDirective.ppIfndef:
    		executeIfdef(lexer, startOffset, false);
    		break;
    	case IPreprocessorDirective.ppIf: 
    		executeIf(lexer, startOffset);
    		break;
    	case IPreprocessorDirective.ppElse: 
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_ELSE)) {
    			fLocationMap.encounterPoundElse(startOffset, ident.getEndOffset(), false);
    			skipOverConditionalCode(lexer, false);
    		} 
    		else {
    			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, startOffset, ident.getEndOffset());
    		}
    		break;
    	case IPreprocessorDirective.ppElif: 
    		int condOffset= lexer.nextToken().getOffset();
    		condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		int endOffset= lexer.currentToken().getEndOffset();
    		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_ELIF)) {
    			fLocationMap.encounterPoundElif(startOffset, condOffset, condEndOffset, endOffset, false);
    			skipOverConditionalCode(lexer, false);
    		} 
    		else {
    			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, startOffset, condEndOffset);
    		}
    		break;
    	case IPreprocessorDirective.ppEndif:
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_END)) {
    			fLocationMap.encounterPoundEndIf(startOffset, ident.getEndOffset());
    		} 
    		else {
    			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, startOffset, ident.getEndOffset());
    		}
    		break;
    	case IPreprocessorDirective.ppWarning: 
    	case IPreprocessorDirective.ppError:
    		condOffset= lexer.nextToken().getOffset();
    		condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		endOffset= lexer.currentToken().getEndOffset();
    		final char[] warning= lexer.getInputChars(condOffset, condEndOffset);
    		final int id= type == IPreprocessorDirective.ppError 
    				? IProblem.PREPROCESSOR_POUND_ERROR 
    				: IProblem.PREPROCESSOR_POUND_WARNING;
    		handleProblem(id, warning, condOffset, condEndOffset); 
    		fLocationMap.encounterPoundError(startOffset, condOffset, condEndOffset, endOffset);
    		break;
    	case IPreprocessorDirective.ppPragma: 
    		condOffset= lexer.nextToken().getOffset();
    		condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		endOffset= lexer.currentToken().getEndOffset();
    		fLocationMap.encounterPoundPragma(startOffset, condOffset, condEndOffset, endOffset);
    		break;
    	case IPreprocessorDirective.ppIgnore:
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		break;
    	default:
    		condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
			endOffset= lexer.currentToken().getEndOffset();
    		handleProblem(IProblem.PREPROCESSOR_INVALID_DIRECTIVE, name, startOffset, condEndOffset);
    		break;
    	}
    }

	private void executeInclude(final Lexer lexer, int poundOffset, boolean include_next, boolean active) throws OffsetLimitReachedException {
		lexer.setInsideIncludeDirective(true);
		final Token header= lexer.nextToken();
		lexer.setInsideIncludeDirective(false);

		int condEndOffset= header.getEndOffset();
		final int[] nameOffsets= new int[] {header.getOffset(), condEndOffset};
		char[] headerName= null;
		boolean userInclude= true;
		
		switch(header.getType()) {
		case Lexer.tSYSTEM_HEADER_NAME:
			userInclude= false;
			headerName = extractHeaderName(header.getCharImage(), '<', '>', nameOffsets);
			condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
			break;
			
		case Lexer.tQUOTE_HEADER_NAME:
			headerName = extractHeaderName(header.getCharImage(), '"', '"', nameOffsets);
			condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
			break;

		case IToken.tCOMPLETION:
			throw new OffsetLimitReachedException(ORIGIN_PREPROCESSOR_DIRECTIVE, header);
			
		case IToken.tIDENTIFIER: 
			TokenList tl= new TokenList();
			condEndOffset= nameOffsets[1]= getTokensWithinPPDirective(lexer, false, tl);
			Token t= tl.first();
			if (t != null) {
				switch(t.getType()) {
				case IToken.tSTRING:
					headerName = extractHeaderName(t.getCharImage(), '"', '"', new int[]{0,0});
					break;
				case IToken.tLT:
					userInclude= false;
					boolean complete= false;
					StringBuffer buf= new StringBuffer();
					t= (Token) t.getNext();
					while (t != null) {
						if (t.getType() == IToken.tGT) {
							complete= true;
							break;
						}
						buf.append(t.getImage());
						t= (Token) t.getNext();
					}
					if (complete) {
						headerName= new char[buf.length()];
						buf.getChars(0, buf.length(), headerName, 0);
					}
				}
			}
			break;
			
		default:
			condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
			break;
		}
		if (headerName == null || headerName.length==0) {
	    	if (active) {
	            handleProblem(IProblem.PREPROCESSOR_INVALID_DIRECTIVE,
	            		lexer.getInputChars(poundOffset, condEndOffset), poundOffset, condEndOffset);
	    	}
			return;
		}

		String path= null;
		boolean reported= false;
		if (active) {
			final File currentDir= userInclude || include_next ? new File(String.valueOf(getCurrentFilename())).getParentFile() : null;
			final CodeReader reader= findInclusion(new String(headerName), userInclude, include_next, currentDir);
			if (reader != null) {
				path= new String(reader.filename);
				if (!isCircularInclusion(path)) {
					reported= true;
					fAllIncludedFiles.add(path);
					ILocationCtx ctx= fLocationMap.pushInclusion(poundOffset, nameOffsets[0], nameOffsets[1], condEndOffset, reader.buffer, path, headerName, userInclude);
					ScannerContext fctx= new ScannerContext(ctx, fCurrentContext, new Lexer(reader.buffer, fLexOptions, this, this));
					fCurrentContext= fctx;
				}
			}
			else {
				handleProblem(IProblem.PREPROCESSOR_INCLUSION_NOT_FOUND, headerName, poundOffset, condEndOffset);
			}
		}
		else {
			// test if the include is inactive just because it was included before (bug 167100)
			final File currentDir= userInclude || include_next ? new File(String.valueOf(getCurrentFilename())).getParentFile() : null;
			path= (String) findInclusion(new String(headerName), userInclude, include_next, currentDir, createPathTester);
			if (path != null) {
				if (fCodeReaderFactory instanceof IIndexBasedCodeReaderFactory) {
					// fast indexer
					if (!((IIndexBasedCodeReaderFactory) fCodeReaderFactory).hasFileBeenIncludedInCurrentTranslationUnit(path)) {
						path= null;
					}
				}
				else {
					// full indexer
					if (!fAllIncludedFiles.contains(path)) {
						path= null;
					}
				}
			}
		}
		if (!reported) {
			fLocationMap.encounterPoundInclude(poundOffset, nameOffsets[0], nameOffsets[1], condEndOffset, headerName, path, userInclude, active); 
		}
	}

	private char[] extractHeaderName(final char[] image, final char startDelim, final char endDelim, int[] offsets) {
		char[] headerName;
		int start= 0;
		int length= image.length;
		if (length > 0 && image[length-1] == endDelim) {
			length--;
			offsets[1]--;
			if (length > 0 && image[0] == startDelim) {
				offsets[0]++;
				start++;
				length--;
			}
		}
		headerName= new char[length];
		System.arraycopy(image, start, headerName, 0, length);
		return headerName;
	}
	
	private boolean isCircularInclusion(String filename) {
		ILocationCtx checkContext= fCurrentContext.getLocationCtx();
		while (checkContext != null) {
			if (filename.equals(checkContext.getFilePath())) {
				return true;
			}
			checkContext= checkContext.getParent();
		}
		return false;
	}


    private void executeDefine(final Lexer lexer, int startOffset) throws OffsetLimitReachedException {
		try {
			ObjectStyleMacro macrodef = fMacroDefinitionParser.parseMacroDefinition(lexer, this);
			fMacroDictionary.put(macrodef.getNameCharArray(), macrodef);
			final Token name= fMacroDefinitionParser.getNameToken();

			fLocationMap.encounterPoundDefine(startOffset, name.getOffset(), name.getEndOffset(), 
					macrodef.getExpansionOffset(), macrodef.getExpansionEndOffset(), macrodef);
		} catch (InvalidMacroDefinitionException e) {
			lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
			handleProblem(IProblem.PREPROCESSOR_INVALID_MACRO_DEFN, e.fName, e.fStartOffset, e.fEndOffset);
		}
    }

    private void executeUndefine(Lexer lexer, int startOffset) throws OffsetLimitReachedException {
		final Token name= lexer.nextToken();
    	final int tt= name.getType();
    	if (tt != IToken.tIDENTIFIER) {
    		if (tt == IToken.tCOMPLETION) {
    			throw new OffsetLimitReachedException(ORIGIN_PREPROCESSOR_DIRECTIVE, name);
    		}
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		handleProblem(IProblem.PREPROCESSOR_INVALID_MACRO_DEFN, name.getCharImage(), startOffset, name.getEndOffset());
    		return;
    	}
		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    	final int endOffset= lexer.currentToken().getEndOffset();
    	final char[] namechars= name.getCharImage();
    	PreprocessorMacro definition= (PreprocessorMacro) fMacroDictionary.remove(namechars, 0, namechars.length);
    	fLocationMap.encounterPoundUndef(definition, startOffset, name.getOffset(), name.getEndOffset(), endOffset, namechars);
    }

    private void executeIfdef(Lexer lexer, int startOffset, boolean positive) throws OffsetLimitReachedException {
		final Token name= lexer.nextToken();
    	final int tt= name.getType();
    	if (tt != IToken.tIDENTIFIER) {
    		if (tt == IToken.tCOMPLETION) {
    			throw new OffsetLimitReachedException(ORIGIN_PREPROCESSOR_DIRECTIVE, name);
    		}
    		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    		handleProblem(IProblem.PREPROCESSOR_DEFINITION_NOT_FOUND, name.getCharImage(), startOffset, name.getEndOffset());
    		return;
    	}
		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
		final int endOffset= lexer.currentToken().getEndOffset();
    	final char[] namechars= name.getCharImage();
        boolean isActive= (fMacroDictionary.get(namechars) != null) == positive;
        
    	fCurrentContext.changeBranch(ScannerContext.BRANCH_IF);        
        if (positive) {
        	fLocationMap.encounterPoundIfdef(startOffset, name.getOffset(), name.getEndOffset(), endOffset, isActive);
        }
        else {
        	fLocationMap.encounterPoundIfndef(startOffset, name.getOffset(), name.getEndOffset(), endOffset, isActive);
        }

        if (!isActive) {
        	skipOverConditionalCode(lexer, true);
        }
    }

    private void executeIf(Lexer lexer, int startOffset) throws OffsetLimitReachedException {
    	boolean isActive= false;
    	TokenList condition= new TokenList();
    	final int condOffset= lexer.nextToken().getOffset();
    	final int condEndOffset= getTokensWithinPPDirective(lexer, true, condition);
    	final int endOffset= lexer.currentToken().getEndOffset();
    	
    	if (condition.first() == null) {
    		handleProblem(IProblem.SCANNER_EXPRESSION_SYNTAX_ERROR, null, startOffset, endOffset);
    	}
    	else {
    		try {
				isActive= fExpressionEvaluator.evaluate(condition, fMacroDictionary);
			} catch (EvalException e) {
				handleProblem(e.getProblemID(), e.getProblemArg(), condOffset, endOffset);
			}
    	}

		fCurrentContext.changeBranch(ScannerContext.BRANCH_IF);
    	fLocationMap.encounterPoundIf(startOffset, condOffset, condEndOffset, endOffset, isActive);
		
    	if (!isActive) {
    		skipOverConditionalCode(lexer, true);
    	} 
    }
    
    /**
     * Runs the preprocessor on the rest of the line, storing the tokens in the holder supplied.
     * Macro expansion is reported to the location map. 
     * In case isCondition is set to <code>true</code>, identifiers with image 'defined' are 
     * converted to the defined-token and its argument is not macro expanded.
     * Returns the end-offset of the last token that was consumed.
     */
    private int getTokensWithinPPDirective(Lexer lexer, boolean isCondition, TokenList result) throws OffsetLimitReachedException {
    	final ScannerContext scannerCtx= fCurrentContext;
    	boolean expandMacros= true;
    	loop: while(true) {
    		Token t= internalFetchToken(expandMacros, true, false, scannerCtx);
    		switch(t.getType()) {
    		case IToken.tEND_OF_INPUT:
    		case IToken.tCOMPLETION:
    			lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE); // make sure the exception is thrown.
    			break loop;
    		case Lexer.tNEWLINE:
    			break loop;
    		case IToken.tIDENTIFIER:
    			if (isCondition && CharArrayUtils.equals(Keywords.cDEFINED, t.getCharImage())) {
    				t.setType(CPreprocessor.tDEFINED);
    				expandMacros= false;	
    			}
    			break;
    		case IToken.tLPAREN:
    			break;
    		default:
    			expandMacros= true;
    			break;
    		}
    		result.append(t);
    	}
    	// make sure an exception is thrown if we are running content assist at the end of the line
    	return lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
    }
    
    private void skipOverConditionalCode(final Lexer lexer, boolean takeElseBranch) throws OffsetLimitReachedException {
    	int nesting= 0;
    	while(true) {
    		final Token pound= lexer.nextDirective();
    		int tt= pound.getType();
    		if (tt != IToken.tPOUND) {
    			if (tt == IToken.tCOMPLETION) {
    				throw new OffsetLimitReachedException(ORIGIN_INACTIVE_CODE, pound); // completion in inactive code
    			}
    			return;
    		}
        	final Token ident= lexer.nextToken();
        	tt= ident.getType();
        	if (tt != IToken.tIDENTIFIER) {
        		if (tt == IToken.tCOMPLETION) {
        			throw new OffsetLimitReachedException(ORIGIN_INACTIVE_CODE, ident);	// completion in inactive directive
        		}
        		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		continue;
        	}
        	
        	// we have an identifier
        	final char[] name = ident.getCharImage();
        	final int type = fPPKeywords.get(name);
        	switch (type) {
        	case IPreprocessorDirective.ppImport:
        	case IPreprocessorDirective.ppInclude:
        		executeInclude(lexer, ident.getOffset(), false, false);
        		break;
        	case IPreprocessorDirective.ppInclude_next:
        		executeInclude(lexer, ident.getOffset(), true, false);
        		break;
        	case IPreprocessorDirective.ppIfdef:
        		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		int endOffset= lexer.currentToken().getEndOffset();
        		nesting++;
        		fCurrentContext.changeBranch(ScannerContext.BRANCH_IF);
        		fLocationMap.encounterPoundIfdef(pound.getOffset(), ident.getOffset(), ident.getEndOffset(), endOffset, false);
        		break;
        	case IPreprocessorDirective.ppIfndef:
        		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		endOffset= lexer.currentToken().getEndOffset();
        		nesting++;
        		fCurrentContext.changeBranch(ScannerContext.BRANCH_IF);
        		fLocationMap.encounterPoundIfndef(pound.getOffset(), ident.getOffset(), ident.getEndOffset(), endOffset, false);
        		break;
        	case IPreprocessorDirective.ppIf: 
        		int condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		endOffset= lexer.currentToken().getEndOffset();
        		nesting++;
        		fCurrentContext.changeBranch(ScannerContext.BRANCH_IF);
        		fLocationMap.encounterPoundIf(pound.getOffset(), ident.getOffset(), condEndOffset, endOffset, false);
        		break;
        	case IPreprocessorDirective.ppElse: 
        		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_ELSE)) {
        			boolean isActive= nesting == 0 && takeElseBranch;
        			fLocationMap.encounterPoundElse(pound.getOffset(), ident.getEndOffset(), isActive);
        			if (isActive) {
        				return;
        			}
        		} 
        		else {
        			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, pound.getOffset(), ident.getEndOffset());
        		}
        		break;
        	case IPreprocessorDirective.ppElif: 
        		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_ELIF)) {
        	    	boolean isActive= false;
        	    	int condOffset= lexer.nextToken().getOffset();
        			if (nesting == 0 && takeElseBranch) {
            	    	TokenList condition= new TokenList();
        				condEndOffset= getTokensWithinPPDirective(lexer, true, condition);
        				if (condition.first() != null) {
        					try {
        						isActive= fExpressionEvaluator.evaluate(condition, fMacroDictionary);
        					} catch (EvalException e) {
        						handleProblem(e.getProblemID(), e.getProblemArg(), condOffset, condEndOffset);
        					}
        				}
        			}
        			else {
        				condEndOffset= lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        			}
        			endOffset= lexer.currentToken().getEndOffset();
        			fCurrentContext.changeBranch(ScannerContext.BRANCH_ELIF);
        	    	fLocationMap.encounterPoundElif(pound.getOffset(), condOffset, condEndOffset, endOffset, isActive);
        			
        	    	if (isActive) {
        	    		return;
        	    	} 
        		} 
        		else {
            		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
            		endOffset= lexer.currentToken().getEndOffset();
        			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, pound.getOffset(), endOffset);
        		}
        		break;
        	case IPreprocessorDirective.ppEndif:
        		lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		if (fCurrentContext.changeBranch(ScannerContext.BRANCH_END)) {
        			fLocationMap.encounterPoundEndIf(pound.getOffset(), ident.getEndOffset());
            		if (nesting == 0) {
            			return;
            		}
            		--nesting;
        		} 
        		else {
        			handleProblem(IProblem.PREPROCESSOR_UNBALANCE_CONDITION, name, pound.getOffset(), ident.getEndOffset());
        		}
        		break;
        	default:
            	lexer.consumeLine(ORIGIN_PREPROCESSOR_DIRECTIVE);
        		break;
        	}
    	}
    }
    
	/**
	 * The method assumes that the identifier is consumed.
	 * <p>
	 * Checks whether the identifier causes a macro expansion. May advance the current lexer
	 * to check for the opening bracket succeeding the identifier.
	 * <p>
	 * If applicable the macro is expanded and the resulting tokens are put onto a new context.
	 * @param identifier the token where macro expansion may occur.
	 * @param lexer the input for the expansion.
	 * @param stopAtNewline whether or not tokens to be read are limited to the current line. 
	 */
	private boolean expandMacro(final Token identifier, Lexer lexer, boolean stopAtNewline) throws OffsetLimitReachedException {
		final char[] name= identifier.getCharImage();
        PreprocessorMacro macro= (PreprocessorMacro) fMacroDictionary.get(name);
        if (macro == null) {
        	return false;
        }
        
        if (macro instanceof FunctionStyleMacro) {
    		Token t= lexer.currentToken();
    		if (!stopAtNewline) {
    			while(t.getType() == Lexer.tNEWLINE) {
    				t= lexer.nextToken();
    			}
    		}
    		if (t.getType() != IToken.tLPAREN) {
    			return false;
    		}
        }
        final boolean contentAssist = fContentAssistLimit>=0 && fCurrentContext == fRootContext;
        TokenList replacement= fMacroExpander.expand(lexer, stopAtNewline, macro, identifier, contentAssist);
    	final IASTName[] expansions= fMacroExpander.clearImplicitExpansions();
    	final ImageLocationInfo[] ili= fMacroExpander.clearImageLocationInfos();
    	final Token last= replacement.last();
    	final int length= last == null ? 0 : last.getEndOffset(); 
    	ILocationCtx ctx= fLocationMap.pushMacroExpansion(
    			identifier.getOffset(), identifier.getEndOffset(), lexer.getLastEndOffset(), length, macro, expansions, ili);
        fCurrentContext= new ScannerContext(ctx, fCurrentContext, replacement);
        return true;
	}

	public Object getAdapter(Class adapter) {
		if (adapter.isAssignableFrom(fLocationMap.getClass())) {
			return fLocationMap;
		}
		return null;
	}

	// old scanner, remove this.
    public CharArrayObjectMap getRealDefinitions() {
    	throw new UnsupportedOperationException();
    }
    public void addDefinition(IMacro macro) {
    	if (macro instanceof IIndexMacro) {
    		addMacroDefinition((IIndexMacro) macro);
    	}
    	else {
    		addMacroDefinition(macro.getSignature(), macro.getExpansion());
    	}
    }
	public IMacro addDefinition(char[] key, char[] value) {
    	throw new UnsupportedOperationException();
	}
	public void setScanComments(boolean val) {
	}
	public IMacro addDefinition(char[] name, char[][] params, char[] expansion) {
    	throw new UnsupportedOperationException();
    }
	public org.eclipse.cdt.internal.core.parser.scanner2.ILocationResolver getLocationResolver() {
		return fLocationMap;
	}
}