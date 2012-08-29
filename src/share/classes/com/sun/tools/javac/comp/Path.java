package com.sun.tools.javac.comp;

import java.util.ArrayList;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class Path {

	private class TreePrint extends TreeScanner {
		private String print;

		public String getPrintString(JCTree tree) {
			scan(tree);
			return print;
		}

		public void visitIf(JCIf tree) {
			print = " if(" + tree.cond + "){}";
		}

		public void visitMethodDef(JCMethodDecl tree) {
			print = tree.name + "(){}";
		}

		public void visitDoLoop(JCDoWhileLoop tree) {
			print = "do {} while(" + tree.cond + ")";
		}

		public void visitWhileLoop(JCWhileLoop tree) {
			print = "while(" + tree.cond + "){}";
		}

		public void visitForLoop(JCForLoop tree) {
			print = "for(" + tree.init + ";..;..){}";
		}

		public void visitForeachLoop(JCEnhancedForLoop tree) {
			print = "foreach() {}";
		}

		public void visitSwitch(JCSwitch tree) {
			print = "switch(" + tree.selector + "){}";
		}

		public void visitCase(JCCase tree) {
			print = "case " + tree.pat;
		}

		public void visitTry(JCTry tree) {
			print = "try {}";
		}

		public void visitCatch(JCCatch tree) {
			print = "catch(" + tree.param + ")";
		}

	}

	public String prettyPrint(JCTree tree) {

		return treePrint.getPrintString(tree);
	}

	public String prettyPrint(JCTree tree, int f) {
		String str = prettyPrint(tree);
		if (f != Path.NIL_FLAG) {
			switch (f) {
			case Path.ELSE_PART:
				str += "[ELSE_PART]";
				break;
			case Path.THEN_PART:
				str += "[THEN_PART]";
				break;
			case Path.FINALLY:
				str += "[FINALLY]";
				break;
			default:
				if (tree.getTag() == JCTree.TRY) {
					str += "[CATCH" + f + "]";
				} else if (tree.getTag() == JCTree.SWITCH) {
					str += "[CASE" + f + "]";
				} else {
					str += "[" + f + "]";
				}
				break;
			}
		}
		return str;
	}

	public class Node {
		// the tree of the node
		private JCTree tree;
		// if needed, use the flag
		private int flag;

		protected Node(JCTree t) {
			tree = t;
			flag = NIL_FLAG;
		}

		public Node() {
			tree = null;
			flag = NIL_FLAG;
		}

		protected Node(JCTree t, int f) {
			tree = t;
			flag = f;
		}

		public int getFlag() {
			return flag;
		}

		public JCTree getRawTree() {
			return tree;
		}

		public boolean hasFlag() {
			return flag != NIL_FLAG;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof Node) {
				Node n = (Node) obj;
				if (this.tree.equals(n.tree) && this.flag == n.getFlag()) {
					return true;
				} else {
					return false;
				}

			}
			return false;
		}

		public String toString() {
			if (flag == NIL_FLAG) {
				return prettyPrint(this.tree);
			} else {
				return prettyPrint(this.tree, flag);
			}
		}

		public boolean isMutex(Node n) {
			if (tree.getTag() != n.tree.getTag()) {
				return false;
			}

			switch (tree.getTag()) {
			case JCTree.IF:
				if ((flag == THEN_PART && n.flag == ELSE_PART)
						|| (flag == ELSE_PART && n.flag == THEN_PART)) {
					return true;
				} else {
					return false;
				}
			case JCTree.SWITCH:
			case JCTree.TRY:
				if (flag != n.flag) {
					return true;
				} else {
					return false;
				}
			default:
				return false;
			}
		}
	}

	// use them if needed
	public final static int NIL_FLAG = 50000; // a magic number:P
	public final static int THEN_PART = NIL_FLAG + 1;
	public final static int ELSE_PART = THEN_PART + 1;
	public final static int FINALLY = ELSE_PART + 1;
	private ArrayList<Node> nodesOnPath;
	private TreePrint treePrint;

	public Path() {
		nodesOnPath = new ArrayList<Node>();
		treePrint = new TreePrint();
	}

	public int size() {
		return nodesOnPath.size();
	}

	/** add a tree without flag to the path */
	public void add(JCTree tree) {
		Node node = new Node(tree);
		nodesOnPath.add(node);
	}

	/**
	 * add a tree with a flag to the path, use Path.{FLAG} as a flag
	 */
	public void add(JCTree tree, int f) {
		Node node = new Node(tree, f);
		nodesOnPath.add(node);
	}

	public void add(Node n) {
		nodesOnPath.add(n);
	}

	public Node getNode(int index) {
		return nodesOnPath.get(index);
	}

	public void setNode(int index, JCTree t) {
		Node n = new Node(t);
		nodesOnPath.set(index, n);
	}

	public void setNode(int index, JCTree t, int f) {
		Node n = new Node(t, f);
		nodesOnPath.set(index, n);
	}

	public int getFlag(int index) {
		return nodesOnPath.get(index).flag;
	}

	public void setPath(Path p) {
		nodesOnPath = new ArrayList<Node>();
		for (int i = 0; i < p.size(); i++) {
			nodesOnPath.add(p.getNode(i));
		}
	}

	public boolean hasFlag(int index) {
		return nodesOnPath.get(index).flag != NIL_FLAG;
	}

	public boolean hasNode(Node n) {
		for (int i = 0; i < size(); i++) {
			if (nodesOnPath.get(i).equals(n)) {
				return true;
			}
		}
		return false;
	}

	public void removeLast() {
		if (nodesOnPath.size() != 0) {
			nodesOnPath.remove(nodesOnPath.size() - 1);
		}
	}

	public Node getLastNode() {
		if (nodesOnPath.isEmpty()) {
			return null;
		} else {
			return nodesOnPath.get(size() - 1);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof Path) {
			Path p = (Path) obj;

			if (size() != p.size()) {
				return false;
			}

			for (int i = 0; i < size(); i++) {
				if (!this.getNode(i).equals(p.getNode(i))) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	public void clear() {
		nodesOnPath.clear();
	}

	public Path getCommonPath(Path path1) {
		Path commonPath = new Path();
		int minSize;
		if (path1.size() > size()) {
			minSize = size();
		} else {
			minSize = path1.size();
		}

		for (int i = 0; i < minSize; i++) {
			if (path1.getNode(i).equals(this.nodesOnPath.get(i))) {
				commonPath.add(path1.getNode(i));
			} else {
				break;
			}
		}
		return commonPath;
	}

	/**
	 * Since some nodes, like try and switch, record their whole tree in the
	 * path, it is not convenient to obtain the common trees of two paths. This
	 * function is for this kind of need.
	 * 
	 * @param path1
	 * @return
	 */
	public Node getCommonNode(Path path1) {
		Path commonPath = getCommonPath(path1);
		return commonPath.getLastNode();

	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{ ");
		if (nodesOnPath.size() != 0) {
			for (int i = 0; i < nodesOnPath.size() - 1; i++) {
				sb.append(nodesOnPath.get(i).toString());
				sb.append(" -> ");
			}
			sb.append(nodesOnPath.get(nodesOnPath.size() - 1).toString());
		}
		sb.append(" }");
		return sb.toString();
	}
}
