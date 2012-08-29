package com.sun.tools.javac.comp;

import java.util.ArrayList;
import java.util.Map;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Path.Node;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class TreeUtil extends TreeScanner {
	// boolean
	private boolean isAllocPoint;
	private boolean isAssignTree;
	private boolean isUsePoint;
	private boolean containTree;

	// symbol
	private Symbol newClassVar;
	private ArrayList<Symbol> symbolArray;
	private Symbol tmpSymbol; // used as tmp

	// mode
	boolean checkUsePoint;
	boolean checkAllocPoint;
	boolean collectSymbol;
	boolean addStats;
	boolean delStats;
	boolean containStats;
	
	//tree
	JCTree addThisTree;
	JCTree delThisTree;
	JCTree containThisTree;
	TouchTree touchTree;
	AnalyzeConstructor analyzeConstructor;
	ArrayList<ClassSymbol> unmovableClasses;

	private TreeMaker make;
	private Types types;

	public TreeUtil(Context context) {
		reset();
		make = TreeMaker.instance(context);
		types = Types.instance(context);
		touchTree = new TouchTree();
		analyzeConstructor = new AnalyzeConstructor();
//		unmovableClasses = new ArrayList<ClassSymbol>();
		unmovableClasses = analyzeConstructor.getUnmovableClasses();
	}

	private void reset() {
		// init boolean variables
		isAllocPoint = false;
		isAssignTree = false;
		isUsePoint = false;
		isAllocPoint = false;
		containTree = false;

		// init symbol variables
		newClassVar = null;
		symbolArray = new ArrayList<Symbol>();
		tmpSymbol = null;

		// init check Mode
		checkUsePoint = false;
		checkAllocPoint = false;
		collectSymbol = false;
		addStats = false;
		delStats = false;
		containStats = false;
		
		// tree
		addThisTree = null;
		delThisTree = null;
		containThisTree = null;
	}

	public void println(String s) {
//		System.out.println("	[treeUtil]	" + s);
	}

	public void concisePrintln(String s) {
		System.out.println(" [TreeUtil] " + s);
	}
	// ===========================================================
	/**
	 * to see if the tree contains a new statement - int i = new Integer(); - i
	 * = new Integer();
	 * 
	 * @param t
	 *            : the tree to be checked
	 * @return true if contains a new statement
	 */
	public boolean isAllocPoint(JCTree t) {
		println("isAllocPoint? " + t);
		reset();
		checkAllocPoint = true;
		if (t.getTag() == JCTree.EXEC ||
			/*omit this may not cause any problems*/	t.getTag() == JCTree.VARDEF) {
			scan(t);
		}
		println("isAllocPoint? return " + isAllocPoint);
		return isAllocPoint;
	}

	public boolean isUnmovableClass(Symbol s){
		println("=====check movable=====");
		println("check  " + s + " in " + unmovableClasses);
		return unmovableClasses.contains(s);
	}
	/**
	 * if the tree contains a new statement, return its varSymbol(not
	 * ClassSymbol) - int i = new Integer(); - i = new Integer(); return i;
	 * 
	 * @param t
	 * @return return the symbol, or null.
	 */
	public Symbol getNewClassVariable(JCTree t) {
		reset();
		checkAllocPoint = true;
		scan(t);
		return newClassVar;
	}

	public boolean containSymbol(JCTree tree, Symbol s) {
		reset();
		checkUsePoint = true;
		tmpSymbol = s;
		scan(tree);
		return isUsePoint;
	}

	public ArrayList<Symbol> collectSymbol(JCTree tree) {
		reset();
		collectSymbol = true;
		scan(tree);
		return symbolArray;
	}

	public boolean isIfTree(JCTree tree) {
		return (tree.getTag() == JCTree.IF);
	}

	public boolean isSingleStatement(JCTree tree) {
		if (tree.getTag() == JCTree.EXEC) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isOnPath(JCTree tree, ArrayList<JCTree> path) {
		for (int i = 0; i < path.size(); i++) {
			if (tree.equals(path.get(i)))
				return true;
		}
		return false;
	}

	// check if last trees in path1 and path2 are in the same if's different
	// part.
	public boolean isMutex(Path path1, Path path2) {
		// first get their commonPaths' size
		// the tree in path[size] should be JCIf
		int size = path1.getCommonPath(path2).size();
		Node n1, n2;
		
		try {
			//try to get the Node whose JCTree is JCIf.
			n1 = path1.getNode(size);
			n2 = path2.getNode(size);
		} catch (Exception e) {
			return false;
		}

		return n1.isMutex(n2);
	}
	
	
	/**
	 * check if tree contains the statement
	 * @param tree
	 * @param statement
	 * @return
	 */
	public boolean containStatement(Node n, JCTree statement) {
		return touchTree.contain(n, statement);
	}

	/**
	 * add statement to the tree in Node n
	 */
	public void addStatement(Node n, JCTree statement) {
		concisePrintln("addStatement " + statement + " to " + n);
		touchTree.addToNode(n, statement);
	}
	/**
	 * remove statement from the tree in Node n
	 * @param n
	 * @param statement
	 */
	public void removeStatement(Node n, JCTree statement){
		concisePrintln("removeStatement " + statement + " from " + n);
		touchTree.removeFromNode(n, statement);
	}

	// ----------------visit method
	@Override
	public void visitAssign(JCAssign tree) {
		println("visitAssign " + tree);
		isAssignTree = true;
		scan(tree.rhs); // we first check the right side. IMPORTANT!!
		scan(tree.lhs);
	}
	
	@Override
	public void visitExec(JCExpressionStatement tree){
		println("visitExec " + tree);
		if ( containStats ){
			//only a single statement in if-then-part or else-part can reach this function.
			if ( tree.equals(containThisTree) || tree.expr.equals(containThisTree)){
				containTree = true;
			} else {
				containTree = false;
			}
			return;
		}
		super.visitExec(tree);
	}
	
	@Override
	public void visitNewClass(JCNewClass tree) {
		println("visitNewClass  " + tree + " primative type?=" + tree.type.isPrimitive());
		if ( collectSymbol == true ) {
			scan(tree.args);
			scan(tree.encl);
		}
		if (checkAllocPoint == true ){
			if ( isUnmovableClass(tree.constructor.owner) == false) {
				println(" the " +  tree.constructor.owner + " isMovable. its allocPoint");
				isAllocPoint = true;
			} else {
				println(" the " +  tree.constructor.owner + " isUnMovable. its not allocPoint");
			}
		}
	}
	
	@Override
	public void visitNewArray(JCNewArray tree){
		println("visitNewArray  " + tree + " collect?" + collectSymbol + " checkAllocPoint?" + checkAllocPoint);
		if ( collectSymbol == true ) {
			scan(tree.elems);
		}
		if ( checkAllocPoint == true) {
			if ( tree.elemtype != null &&
					tree.elemtype.getTag() == JCTree.IDENT ){
					Symbol sym = ((JCIdent)tree.elemtype).sym;
					if ( sym instanceof ClassSymbol ){
						println("sys is ClassSymbol");
						if ( isUnmovableClass(sym) == false){
							println(sym + " isMovableClass. this is allocPoint");
							isAllocPoint = true;
						} else {
							println(sym + " isUnmovableClass. This is not allocPoint");
							isAllocPoint = false;
						}
					} else {
						println("The elem type " + sym + " is not ClassSymbol. Mark to isAllocPoint");
						isAllocPoint = true;
					}
			} else {
				// float[] i = {1, 2, 3};
				//TODO: had better to redesign
				isAllocPoint = true;
			} 
		} 
		
	}

	@Override
	public void visitVarDef(JCVariableDecl tree) {
		println("visitVarDecl " + tree);
		if (tree.init != null) {
			scan(tree.init);
			if (isAllocPoint == true) {
				if ( tree.sym.isLocal() ){
					newClassVar = tree.sym;
				} else {
					isAllocPoint = false;
				}
				println("find!!!" + newClassVar);
			}
		}
	}
	
	/**
	 * int[] f = {1,3}; f[2] = new Integer(2); 
	 * we won't move f[2]
	 */
	@Override
	public void visitIndexed(JCArrayAccess tree) {
		println("visitIndexed " + tree);
		isAllocPoint = false;
		super.visitIndexed(tree);
		isAllocPoint = false; // this is to prevent indexed changed this variable.
	}
	
	public void visitSelect(JCFieldAccess tree) {
		if ( collectSymbol == true ) {
			if ( tree.sym instanceof VarSymbol &&
					! symbolArray.contains(tree.sym)) {
				symbolArray.add(tree.sym);
			}
		}
		scan(tree.selected);
	}
	
	@Override
	public void visitConditional(JCConditional tree) {
		super.visitConditional(tree);
		// for instance, i = (i1>2)?new Integer(1): 3;
		// it should not be regarded as allocation point.
		if ( isAllocPoint == true ) {
			isAllocPoint = false;
		}
	}
	

	public void visitIdent(JCIdent tree) {
		println("visitIdent " + tree);
		if (isAssignTree == true) {
			if (isAllocPoint == true && newClassVar == null) { // Coming from
																// the new
																// statement
				println("newClassVar = " + tree.sym + " type:" + tree.sym.type + " isPrimative?=" + tree.sym.type.isPrimitive());
				if ( tree.sym.isLocal() ) {
					newClassVar = tree.sym;
				} else {
					println(tree.sym + " is not local, isAllocPoint=false");
					isAllocPoint = false;
				}
			}
		}
		if (checkUsePoint == true) {
			println("checking UsePoint " + tmpSymbol + " vs. " + tree.sym);
			if (tmpSymbol.equals(tree.sym))
				isUsePoint = true;
		}

		if (collectSymbol == true ) {
			if (tree.sym instanceof VarSymbol
				&& !symbolArray.contains(tree.sym)) {
				symbolArray.add(tree.sym);
			} 
		}

	}

	/**
	 * This class is main for test if a node contains 
	 * a tree, or insert/ delete a tree from the tree in a node.
	 */
	public class TouchTree extends TreeScanner{
		private Node n;
		private int scanMode;
		public final static int CHECK_CONTAIN = 0;
		public final static int ADD = CHECK_CONTAIN + 1;
		public final static int REMOVE = ADD + 1;
		public boolean contain;
		public JCTree statement;
		
		public boolean contain(Node enclosure, JCTree stat){
			scanMode = CHECK_CONTAIN;
			statement = stat;
			contain = false;
			n = enclosure;
			scan(enclosure.getRawTree());
			return contain;
		}
		public void addToNode(Node enclosure, JCTree stat){
			scanMode = ADD;
			statement = stat;
			n = enclosure;
			scan(enclosure.getRawTree());
		}
		public void removeFromNode(Node enclosure, JCTree stat){
			scanMode = REMOVE;
			statement = stat;
			n = enclosure;
			scan(enclosure.getRawTree());
		}
		public void visitMethodDef(JCMethodDecl tree){
			switch(scanMode){
			case CHECK_CONTAIN:
				scan(tree.body.stats);
				break;
			case ADD:
				tree.body.stats = tree.body.stats.prepend((JCStatement)statement);
				break;
			case REMOVE:
				tree.body.stats = remove(tree.body.stats, statement);
				break;
			}
		}
		public void visitIf(JCIf tree){
			JCStatement part = ((n.getFlag() == Path.THEN_PART) ?
					tree.thenpart : tree.elsepart);
			switch(scanMode){
			case CHECK_CONTAIN:
					if ( part.getTag() == JCTree.BLOCK) {
						scan(part);
					} else {
						//single statement
						if ( part.equals(statement)){
							contain = true;
						}
					}
				
				break;
			case ADD:
				if ( part.getTag() != JCTree.BLOCK){
					// add to a single statement. Wrap a Block.
					List<JCStatement> newpart = List.of((JCStatement)statement, part);
					if ( n.getFlag() == Path.THEN_PART ){
						tree.thenpart = make.Block(0, newpart);
					} else {
						tree.elsepart = make.Block(0, newpart);
					}
				} else {
					JCBlock block = (JCBlock)part;
					block.stats = block.stats.prepend((JCStatement)statement);
				}
				break;
			case REMOVE:
				if ( part.getTag() != JCTree.BLOCK ){
					
					if ( n.getFlag() == Path.THEN_PART){
						tree.thenpart = make.Skip();
					} else {
						tree.elsepart = make.Skip();
					}
				} else {
					JCBlock block = (JCBlock)part;
					block.stats = remove(block.stats, statement);
				}
				break;
			}
		}
		
		public void visitSwitch(JCSwitch tree){
			int caseNum = n.getFlag();
			scan( tree.cases.get(caseNum));
		}
		
		public void visitTry(JCTry tree){
			int catchNum = n.getFlag();
			if ( catchNum == Path.NIL_FLAG ) {
				switch(scanMode){
				case CHECK_CONTAIN:
					scan(tree.body.stats);
					break;
				case ADD:
					tree.body.stats = tree.body.stats.prepend((JCStatement)statement);
					break;
				case REMOVE:
					tree.body.stats = remove(tree.body.stats, statement);
					break;
				}
			} else if ( catchNum == Path.FINALLY){
				switch(scanMode){
				case CHECK_CONTAIN:
					scan(tree.finalizer.stats);
					break;
				case ADD:
					tree.finalizer.stats = tree.finalizer.stats.prepend((JCStatement)statement);
					break;
				case REMOVE:
					tree.finalizer.stats = remove(tree.finalizer.stats, statement);
					break;
				}
			} else {
				scan( tree.catchers.get(catchNum));
			}
		}
		public void scan(List<? extends JCTree> trees){
			if ( scanMode == CHECK_CONTAIN ){
				contain = false;
				for (int i = 0; i < trees.size(); i++){
					if ( trees.get(i).equals(statement)){
						contain = true;
						break;
					}
				}
				return;
			} else {
				super.scan(trees);
			}
		}
		
		private List<JCStatement> remove(List<JCStatement> stats, JCTree statement){
			ListBuffer<JCStatement> newStats = ListBuffer.lb();
			for (int i = 0; i < stats.size(); i++){
				if (stats.get(i).equals(statement)){
					continue;
				} else {
					newStats.append(stats.get(i));
				}
			}
			return newStats.toList();
		}
		public void visitCase(JCCase tree){
			switch (scanMode){
			case CHECK_CONTAIN:
				scan(tree.stats);
				break;
			case ADD:		
				tree.stats = tree.stats.prepend((JCStatement)statement);
				break;
			case REMOVE:
				tree.stats = remove(tree.stats, statement);
				break;
			}
		}
		public void visitCatch(JCCatch tree){
			switch(scanMode){
			case CHECK_CONTAIN:
				scan(tree.body.stats);
				break;
			case ADD:
				tree.body.stats = tree.body.stats.prepend((JCStatement)statement);
				break;
			case REMOVE:
				tree.body.stats = remove(tree.body.stats, statement);
				break;
			}
		}
		/********************************************************
		 * it is impossible to add a statement to a loop, 
		 * here only to handle checking enclosure and remove
		 **********************************************************/
		public void visitLoopBody(JCStatement body){
			if ( scanMode == CHECK_CONTAIN ){
				if ( body.getTag() != JCTree.BLOCK) {
					if ( body.equals(statement)) {
						contain = true;
						return;
					}
				} else {
					scan(body);
				}
			} else if ( scanMode == REMOVE ){
				if ( body.getTag() != JCTree.BLOCK ){
					if ( body.equals(statement)){
						body = make.Skip();
					}
				} else {
					JCBlock block = (JCBlock)body;
					block.stats = remove(block.stats, statement);
				}
			} else {
				println("Error occur! add a allocation statement into loop");
			}
		}
		public void visitForLoop(JCForLoop tree){
			visitLoopBody(tree.body);
		}
		public void visitWhileLoop(JCWhileLoop tree){
			visitLoopBody(tree.body);
		}
		public void visitDoLoop(JCDoWhileLoop tree){
			visitLoopBody(tree.body);
		}
		public void visitEnhancedForLoop(JCEnhancedForLoop tree){
			visitLoopBody(tree.body);
		}
	}
	
	
	/**
	 * Analyze the constructor of a class to see if the class
	 * is movable.
	 * @author ljs
	 *
	 */
	public class AnalyzeConstructor extends TreeScanner{
		private boolean movable;
		public boolean isMovable(JCClassDecl tree){
			movable = true;
			scan(tree);
			println("checkMovable return " + movable);
			return movable;
		}
		
		public void println(String s){
//			concisePrintln("	[AnalyzeConstructor]	" + s);
		}
		public ArrayList<ClassSymbol> getUnmovableClasses(){
			
			ArrayList<ClassSymbol> unmovableSyms = new ArrayList<ClassSymbol>();
			ArrayList<ClassSymbol> movableSyms = new ArrayList<ClassSymbol>();
			
			// pass1, get obvious unmovable class
			for (Map.Entry<ClassSymbol, JCClassDecl> entry : JavaCompiler.classSymbolInitMap.entrySet()){
				if ( ! isMovable(entry.getValue()) ){
					//println("Add " + entry.getKey() + " to unmovableSet");
					unmovableSyms.add(entry.getKey());
				} else {
					if ( entry.getKey() == null ) {
						
					}
					movableSyms.add(entry.getKey());
				}
			}
			// pass2, check those movable class if their super class is unmovable.
			int totalMovable = movableSyms.size(); 
			int totalUnmovable = unmovableSyms.size();
//			concisePrintln("movableSyms: " + movableSyms);
//			concisePrintln("unmovableSyms: " + unmovableSyms);
			for ( int i = 0; i < totalMovable; i++) {
//				concisePrintln("movableSym: " + movableSyms.get(i));
				for ( int j = 0; j < totalUnmovable; j++) {
//					concisePrintln("	unmovable symbol: " + unmovableSyms.get(j));
					if ( (! movableSyms.get(i).equals(unmovableSyms.get(j))) && 
							movableSyms.get(i).isSubClass(unmovableSyms.get(j), types)) {
						unmovableSyms.add(movableSyms.get(i));
					}
				}
			}
			
			println("unMovable set: " + unmovableSyms);
			return unmovableSyms;
		}
		@Override 
		public void visitClassDef(JCClassDecl tree){
			println("=======Class======" + tree.sym);
			movable = true;
			for (int i = 0; i < tree.defs.size(); i++){
				if ( tree.defs.get(i).getTag() == JCTree.METHODDEF ){
					scan(tree.defs.get(i));
				}
			}
		}
		public void visitMethodDef(JCMethodDecl tree){
			//Analyze constructor
			if ( ! tree.sym.isConstructor() ){
				return;
			}
			
			for (int i = 0; i < tree.body.stats.size(); i++){
				println("Scan " + tree.body.stats.get(i));
				scan(tree.body.stats.get(i));
				if ( movable == false ){
					return;
				}
			}
		}
	
		public void visitIdent(JCIdent tree) {
			println("visitIdent " + tree);
			Symbol sym = tree.sym;
			if ( sym.kind == Kinds.VAR  ) {
				if ( sym.isStatic() ){
					movable = false;
				}
			} else {
				println(sym + " not var Symbol");
			}
			println("visitIdent end isStatic=" + movable);
		}
	}

}
