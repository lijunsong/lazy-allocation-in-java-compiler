package com.sun.tools.javac.comp;

import java.util.ArrayList;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.comp.Path.Node;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCAssert;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class Delay extends TreeScanner {

	protected static final Context.Key<Delay> delayKey = new Context.Key<Delay>();
	private TreeMaker make;
	private Context c; 
	

	public static Delay instance(Context context) {
		Delay instance = context.get(delayKey);
		if (instance == null)
			instance = new Delay(context);
		return instance;
	}

	protected Delay(Context context) {
		context.put(delayKey, this);
		this.c = context;

	}
	
	public class Point {
		private Path path;
		private JCTree statement;
		public Point() {
			path = new Path();
			statement = null;
		}
		public Point(Path p, JCTree t) {
			this();
			setPath(p);
			setStatement(t);
		}
		public void setPath(Path p) {
			path.setPath(p);
		}
		public void setStatement(JCTree s) {
			statement = s;
		}
		public String toString() {
			return "\n\t<#statement: " +
					statement +
					" ; path: " +
					path + 
					"#>";
					
		}
		
	}
	
	public class AllocUse {
		private Symbol sym;
//		private JCTree objectTree;
		private ArrayList<Point> usePointSet;
		private Point allocPoint;
		public AllocUse() {
			sym = null;
//			objectTree = null;
			usePointSet = new ArrayList<Point>();
			allocPoint = new Point();
		}
		public void setAllocStatement(JCTree t) {
			allocPoint.setStatement(t);
		}
		public void setAllocPath(Path p) {
			allocPoint.setPath(p);
		}
		public void setAllocPoint(Point p) {
			allocPoint = p;
		}
		public Path getAllocPath() {
			return allocPoint.path;
		}
		public JCTree getAllocStatement() {
			return allocPoint.statement;
		}
		public void addToUsePointSet(Point u) {
			usePointSet.add(u);
		}
		public boolean usePointContainsTree(JCTree tree) {
			for (int i = 0; i < usePointSet.size(); i++) {
				if (usePointSet.get(i).statement.equals(tree)) {
					return true;
				}
			}
			return false;
		}
		public String toString() {
			return "\n\nsym: " +
					sym +
					"\nallocPoint: " +
					allocPoint +
					"\nusePointSet: " +
					usePointSet;
		}
	}
	
	private ArrayList<AllocUse> allocUseSet;
	private TreeUtil treeUtil;
	private Path usePath;
	private ArrayList<Symbol> paramsSymbols;
	
	private void println(String s) {
		//System.out.println("	[Delay]	" + s);
	}
	
	private void concisePrintln(String s) {
		System.out.println(" [Delay] " + s );
	}
	
	/**
	 * add use point to current allocation&use set.
	 * @param usePoint
	 * @param useSymbols   the symbols that are allocated by new statement
	 * 					   in usePoint.
	 */
	private void addUsePoint(Point usePoint, ArrayList<Symbol> useSymbols) {
		println("##############");
		println("prepare to addUsePoint " + usePoint + " which has useSymbols: " + useSymbols);
		println("current allocUseSet: " + allocUseSet);
		println("###############");
		
		for (int i = 0; i < allocUseSet.size(); i++) {
			Symbol previousAllocSymbol = allocUseSet.get(i).sym;
			println("checking " + previousAllocSymbol);
			if ( ! useSymbols.contains(previousAllocSymbol)) {
				println("useSymbols do not contain " + previousAllocSymbol);
				continue;
			}
			Path previousAllocPath = allocUseSet.get(i).getAllocPath();
			
			if ( treeUtil.isMutex(usePoint.path, previousAllocPath)) {
				println(" isMutex return: " + usePoint.path + " ___ " + previousAllocPath);
				continue;
			}
			
			// last allocation point
			if ( i == allocUseSet.size() - 1 ) {
				//reaching here means that the usePoint contains the 
				// previousAllocSymbol, and they are not separated in the 
				// then-else parts.
				println(" last allocation Point: add: " + usePoint);
				allocUseSet.get(i).addToUsePointSet(usePoint);
				break;
			}
			
			boolean newICanPassNewJ = true;
			for (int j = i + 1; j < allocUseSet.size(); j++) {
				Symbol latterAllocSymbol = allocUseSet.get(j).sym;
				if ( ! previousAllocSymbol.equals(latterAllocSymbol) ) {
					println("different new symbol: " + previousAllocSymbol + " __ " + latterAllocSymbol);
					continue;
				}
				Path latterAllocPath = allocUseSet.get(j).getAllocPath();
				if ( treeUtil.isMutex(usePoint.path, latterAllocPath)) {
					newICanPassNewJ = true;
					println("4");
					continue;
				}
				
				Node common1 = previousAllocPath.getCommonNode(latterAllocPath);
				JCTree latterAllocStatement = allocUseSet.get(j).getAllocStatement();
				println("preivous new & latter new's common1: " + common1);
				
				// if common1 does not contain the latter allocation statement.
				if ( ! treeUtil.containStatement(common1, latterAllocStatement)) {
//					println("common1 doesnot contain latter allocation statement");
					if ( usePoint.path.hasNode(common1) ) {
						println("use path has common1");
						Node common2 = latterAllocPath.getCommonNode(usePoint.path);
						
						println("latterAllocpath: " + latterAllocPath);
						println("usePoint.path " + usePoint.path);
						println("common2: " + common2);
						if ( treeUtil.containStatement(common2, latterAllocStatement)) {
							println("common2 contain latter allocatin, break;");
							newICanPassNewJ = false;
							break;
						} else {
							println("common2 does not contain " + latterAllocStatement);
							continue;
						}
					} else { 
						continue;
					}
				} else {
					//if common1 does contain the latter allocation point.
					println("common1 contains latter allocation statement, break;");
					newICanPassNewJ = false;
					break;
				}
			}
			if ( newICanPassNewJ == true ) {
				allocUseSet.get(i).addToUsePointSet(usePoint);
				println("add " + usePoint.statement + " to "
						+ allocUseSet.get(i).allocPoint);
			}
		}	
	}

	/**
	 * The tree is suspected as a usePoint; if it is, add to 
	 * corresponding allocation and use point set.
	 */
	private void maybeUsePoint(JCTree tree) {
		println("maybeUsePoint: " + tree);
		if ( allocUseSet.size() == 0 ) {
			println("empty set: return");
			return;
		}
		
		// collect the symbols in tree that are the symbols allocated
		// by new.
		ArrayList<Symbol> symbols = 
				allocSymbolsInStatement(tree);
		println("symbols in tree that is allocated by new: " + symbols);
		if ( symbols.isEmpty() ) return;
		
		// tree contains Symbol allocated by new statement
		
		Point usePoint = new Point();
		usePoint.setPath(usePath);
		usePoint.setStatement(tree);
		
		addUsePoint(usePoint, symbols);
		
	}
		//where
		private ArrayList<Symbol> allocSymbolsInStatement(JCTree t) {
			ArrayList<Symbol> syms = new ArrayList<Symbol>();
			
			ArrayList<Symbol> symsInTree = treeUtil.collectSymbol(t);
			println("collect all Symbols in " + t + " : " + symsInTree );
			for (int i = 0; i < symsInTree.size(); i++) {
				for (int j = 0; j < allocUseSet.size(); j++) {
					if ( symsInTree.get(i).equals(allocUseSet.get(j).sym)) {
						syms.add(symsInTree.get(i));
						break;
					}
				}
			}
			return syms;
		}
	/**
	 * This interface is for somepoint that is both allocPoint and usePoint.
	 * Since if allocPoint is moved, its usePoint's path should be updated also.
	 * we can automatically update the path if allocPoint's path is the same with usePoint's path
	 * That's why we add a Point as a parameter.
	 * @param t
	 * @param p
	 */
	private void maybeUsePoint(JCTree t, Point p) {
		println("maybeUsePoint with p: " + t + " | " + p);
		if ( allocUseSet.size() == 0 ) {
			println("empty set: return");
			return;
		}
		
		// collect the symbols in tree that are the symbols allocated
		// by new.
		ArrayList<Symbol> symbols = 
				allocSymbolsInStatement(t);
		println("symbols in tree that is allocated by new: " + symbols);
		if ( symbols.isEmpty() ) return;
		
		addUsePoint(p, symbols);
	}
	private void maybeUsePoint(List<? extends JCTree> trees) {
		for (int i = 0; i < trees.size(); i++) {
			maybeUsePoint(trees.get(i));
		}
	}
	
	public int startMove(){
		int totalMoveNumber = 0;
		
		// when moving tree, we start from the last node
		for (int i = allocUseSet.size()-1; i >= 0; i--){
			if ( allocUseSet.get(i).getAllocStatement() == null ) {
				//it's parameter, we do not move this.
				continue;
			}
			Path moveToPath = getCommonUsePath(allocUseSet.get(i).usePointSet);
			if ( moveToPath == null ){
				//if usePointSet is empty
				continue;
			}
			
			Path allocPath = allocUseSet.get(i).getAllocPath();
			Path moveFromPath = allocPath.getCommonPath(moveToPath);
			// check if the tree from moveFromPath to moveToPath is
			// a loop. If there are any loops, adjust the moveToPath
			// to the Node before the loop
			Path realMoveToPath = new Path();
			realMoveToPath.setPath(moveFromPath);
			for (int j = moveFromPath.size(); j < moveToPath.size(); j++){
				switch ( moveToPath.getNode(j).getRawTree().getTag() ){
				case JCTree.FOREACHLOOP:
				case JCTree.FORLOOP:
				case JCTree.WHILELOOP:
				case JCTree.DOLOOP:
					// the path will be cut off by these node
					j = moveToPath.size();
					break;
				default:
					realMoveToPath.add(moveToPath.getNode(j));
				}
			}
			moveToPath.setPath(realMoveToPath);

			Node moveFromNode = moveFromPath.getLastNode();
			if ( moveFromNode == null) concisePrintln(" 1 " + allocUseSet.get(i).toString());
			if ( moveToPath == null) concisePrintln(" 2 " + allocUseSet.get(i).toString());
			if ( moveFromNode.equals(moveToPath.getLastNode())){
				continue;
			}
			
			if ( treeUtil.containStatement( 
					moveFromNode,
					allocUseSet.get(i).getAllocStatement())){
				// newTree should be move to moveToNode

				allocUseSet.get(i).allocPoint.path = moveToPath;  //use =, DO NOT USE SETPATH FUNCTION
				moveTree( moveFromNode, moveToPath.getLastNode(), allocUseSet.get(i).getAllocStatement());

				totalMoveNumber++;
				
			} else {
				continue;
			}
		}
		return totalMoveNumber;
	}
	
	/**
	 * move the allocation statement from one Node to another.
	 * @param from       from which Node
	 * @param to		 move to which Node
	 * @param allocStatement  
	 */
	public void moveTree(Node from, Node to, JCTree allocStatement){
		if ( allocStatement.getTag() == JCTree.VARDEF ){
			
			JCVariableDecl vardef = (JCVariableDecl) allocStatement;
			JCIdent nameIdent = make.Ident(vardef.sym);
			JCAssign newAssign = make.Assign(nameIdent, vardef.init);
			newAssign.type = vardef.type;
			
			JCExpressionStatement finalAssign = make.Exec(newAssign);
			finalAssign.type = newAssign.type;
			vardef.init = null;
			
			treeUtil.addStatement(to, finalAssign);
		} else {
			treeUtil.removeStatement(from, allocStatement);
			treeUtil.addStatement(to, allocStatement);
		}
	}
	
	public Path getCommonUsePath(ArrayList<Point> usePointSet){
		if ( usePointSet.isEmpty() ){
			return null;
		}
		Path commonPath = usePointSet.get(0).path;
		for (int i = 1; i < usePointSet.size(); i++){
			commonPath = commonPath.getCommonPath(usePointSet.get(i).path);
		}
		return commonPath;
	}
	
	/**************************************************************************
	 * visitors
	 *************************************************************************/

	public void visitMethodDef(JCMethodDecl tree) {
		//get the symbols in parameter.
		paramsSymbols.clear();
		for ( JCVariableDecl param : tree.params){
			paramsSymbols.add(param.sym);
		}
		
		// add parameter trees.
		usePath.add(tree);
		println("visit Method: " + usePath.getNode(0));
		
		// add parameters' symbol to AllocUseSet
		// FAKE alloc point! its a trick
		for ( JCVariableDecl param : tree.params){
			if ( param.sym.type.isPrimitive() ) {
				//do not record param that is int float .... 
				continue;
			}
			AllocUse u = new AllocUse();
			u.sym = param.sym;
			u.setAllocStatement(null);
			u.setAllocPath(usePath);
			allocUseSet.add(u);			
		}
		
		//start scan
		if ( tree.body != null) // an interface's body will be nill
			scan(tree.body.stats);
		println("*******************************");
		println(allocUseSet.toString());
		println("************Move***********");
		int number = startMove();
		println("######################### finally move " +  number + " Nodes ########################");
		println(tree.toString());
		println("------------------------------");
//		println(allocUseSet.toString());
			//concisePrintln(allocUseSet.toString()); 
			concisePrintln(" [" + number + "]	" + tree.sym.outermostClass() + ":" + tree.sym);
			concisePrintln(tree.toString());
		
		usePath.clear();
		allocUseSet.clear();
	}
	
	/** Skip static block  */
	public void visitBlock(JCBlock tree) {
		if ( tree.isStatic() ) return;
		super.visitBlock(tree);
	}
	public void visitVarDef(JCVariableDecl tree) {
		println("Delay.visitVarDef: " + tree);
		if ( ! tree.sym.isLocal() ) {
			return;
		}
		Point p = new Point(usePath, tree);
		if ( tree.init != null ) {
			if ( tree.init.getTag() == JCTree.NEWCLASS ||
					tree.init.getTag() == JCTree.NEWARRAY ) {
				maybeUsePoint(tree.init, p);
				//check if is movable
				switch(tree.init.getTag()){
				case JCTree.NEWCLASS:
					JCNewClass t = (JCNewClass)tree.init;
					if ( treeUtil.isUnmovableClass(t.constructor.owner)){
						println( tree + " is unmovable.");
						return;
					}
					break;
				case JCTree.NEWARRAY:
					println("NEWARRAY " + tree.init);
					JCNewArray ta = (JCNewArray)tree.init;
					if ( ta.elemtype != null && ta.elemtype.getTag() == JCTree.IDENT ){
						Symbol sym = ((JCIdent)ta.elemtype).sym;
						if ( sym instanceof ClassSymbol ){
							if ( treeUtil.isUnmovableClass(sym)){
								println( tree + " is unmovable.");
								return;
							}
						}
					}
					break;
				}
				//------end
				
				AllocUse u = new AllocUse();
				u.sym = tree.sym; 
				u.setAllocPoint(p);
				allocUseSet.add(u);
			} else {
				maybeUsePoint(tree.init, p);
			}
		}
	}
	
	public void visitDoLoop(JCDoWhileLoop tree) {
		usePath.add(tree);
		scan(tree.body);
		usePath.removeLast();
		
		maybeUsePoint(tree.cond);
	}
	
	public void visitWhileLoop(JCWhileLoop tree) {
		maybeUsePoint(tree.cond);
		
		usePath.add(tree);
		scan(tree.body);
		maybeUsePoint(tree.cond);		//simulate the loop
		usePath.removeLast();
	}
	
	public void visitForLoop(JCForLoop tree) {
		maybeUsePoint(tree.init);
		maybeUsePoint(tree.cond);
		maybeUsePoint(tree.step);
		
		usePath.add(tree);
		scan(tree.body);
		maybeUsePoint(tree.step);		//simulate the loop
		maybeUsePoint(tree.cond);		//simulate the loop
		usePath.removeLast();
	}
	
	/** 
	 * since foreach will lock the variable, no need to simulate the
	 * loop */
	public void visitForeachLoop(JCEnhancedForLoop tree) {
		maybeUsePoint(tree.expr);
		
		usePath.add(tree);
		scan(tree.body);
		usePath.removeLast();
	}
	
	public void visitSwitch(JCSwitch tree) {
		maybeUsePoint(tree.selector);
		
		for (int i = 0; i < tree.cases.size(); i++) {
			usePath.add(tree, i);
			scan(tree.cases.get(i));
			usePath.removeLast();
		}
	}
	
	public void visitTry(JCTry tree) {
		usePath.add(tree);
		scan(tree.body.stats);
		usePath.removeLast();
		
		//catch
		for (int i = 0; i < tree.catchers.size(); i++) {
			usePath.add(tree, i);   //set flag as which catch currently is
			scan(tree.catchers.get(i).body);
			usePath.removeLast();
		}
		
		//finally
		if ( tree.finalizer != null ) {
			usePath.add(tree, Path.FINALLY);
			scan(tree.finalizer);
			usePath.removeLast();
		}
	}
	
	public void visitConditional(JCConditional tree) {
		maybeUsePoint(tree.cond);
		maybeUsePoint(tree.truepart);
		maybeUsePoint(tree.falsepart);
	}
	
	@Override
	public void visitIf(JCIf tree) {
		maybeUsePoint(tree.cond);
		
		//check then part
//		if ( tree.thenpart.getTag() != JCTree.BLOCK ) {
//			List<JCStatement> then = List.of(tree.thenpart);
//			tree.thenpart = make.Block(0, then);
//		}
		
		usePath.add(tree, Path.THEN_PART);
		scan(tree.thenpart);
		usePath.removeLast();
		
		//check else part
		if ( tree.elsepart != null ) {
//			if ( tree.elsepart.getTag() != JCTree.BLOCK) {
//				println(tree.elsepart + " != BLOCK ");
//				List<JCStatement> els = List.of(tree.elsepart);
//				tree.elsepart = make.Block(0, els);
//			}
			usePath.add(tree, Path.ELSE_PART);
			scan(tree.elsepart);
			usePath.removeLast();
		}
	}
	
	public void visitReturn(JCReturn tree) {
		maybeUsePoint(tree);
	}
	
	public void visitAssert(JCAssert tree) {
		maybeUsePoint(tree);
	}
	
	public void visitApply(JCMethodInvocation tree) {
		maybeUsePoint(tree);
	}
	
	public void visitNewClass(JCNewClass tree) {
		maybeUsePoint(tree.args);
	}
	
	/**
	 * DO NOT SCAN(TREE.EXPR), which will finally make equals function
	 * failed.
	 */
	public void visitExec(JCExpressionStatement tree) {
		println("treeExec expr " + tree + " tag: " + tree.expr.getTag());
		Point p = new Point(usePath, tree);
		if ( treeUtil.isAllocPoint(tree) && tree.expr.getTag() == JCTree.ASSIGN) {
			//prevent the anonymous new: new Integer(3).getValue()
			// class.i = new Integer(2);
			// class = new Integer(2);
			JCAssign assign = (JCAssign)tree.expr;
			
			maybeUsePoint(assign.rhs, p); // in case of new Integer(myvariable);
			
			if ( assign.lhs.getTag() == JCTree.SELECT ){
				JCFieldAccess select = (JCFieldAccess)assign.lhs;
				//the select tree may be use point, first we check this
				maybeUsePoint(assign.lhs, p);
				//next, check if current select was added to the parameter symbol useset.
				for (int i = 0; i < allocUseSet.size(); i++) {
					if ( allocUseSet.get(i).getAllocStatement() == null) {
						//allocUseSet.get(i) is parameter symbol.
						if ( allocUseSet.get(i).usePointContainsTree(tree)) {
							//this select won't be counted as alloc point, since it refers to the parameter's space
							//even if it has new statement
							println("select " + select + " refer to paras sym! it's not allocPoint");
							return;
						}
					} else {
						// because the parameter symbol is at the head of "set"
						break;
					}
				}
				
				//if reach here, it means that the select tree does not refer to the 
				// parameter symbol, it can be moved.
				AllocUse u = new AllocUse();
				u.sym = select.sym;
				u.setAllocPoint(p);
				allocUseSet.add(u);
				
			} else if ( assign.lhs.getTag() == JCTree.IDENT ){
				AllocUse u = new AllocUse();
				u.sym = ((JCIdent)(((JCAssign)tree.expr).lhs)).sym;
				u.setAllocPoint(p);
				allocUseSet.add(u);	
			} else {
				println("visitExec " + tree + " UNKOWN TREE NODE*********************");
			}

		} else {
			println("NOT ALLOC POINT OR NOT ASSIGN");
			maybeUsePoint(tree);
		}
	}

	public void visitIndexed(JCArrayAccess tree) {
		maybeUsePoint(tree);
	}
	/**************************************************************************
	 * main method
	 *************************************************************************/

	public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
		JCTree tree = env.tree;
		allocUseSet = new ArrayList<AllocUse>();
		treeUtil = new TreeUtil(this.c);
		usePath = new Path();
		this.make = make;
		paramsSymbols = new ArrayList<Symbol>();
		
		scan(tree);
	}
}
