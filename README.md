Lazy Allocation in Java Compiler
================================

Lazy allocation implemented in the sun Java compiler. This project is actually one part of my term thesis that is about optimizing objects allocation in compilers.

Lazy Allocation
---------------

There are many ways to delay the allocation of objects, during run-time or static compiling time. This implementation will delay the allocation at compiling time.

Here is an example to illustrate what lazy allocation does in the compiler:

    public class Sample{
        public static void main(String[] args){
            int i = 3;
            int j = new Integer(5);
            if ( i < 5 ) {
            } else {
                j = j + 1;
            }
    }

After compiling this program, the binary code should be the same with the binary code built from the following code, in which the allocation of an Integer object is moved to the else-part of the if statement. Since the program will not necessarily reach the else-part, this allocation of object will not necessarily happen, which means a potential delay of allocation.

    public class Sample{
        public static void main(String[] args){
            int i = 3;
            int j; 
            if ( i < 5 ) {
            } else {
                j = new Integer(5);
                j = j + 1;
            }
    }

Compilers
---------

There are only a few files changed in this Java compiler compared with the compiler in OpenJDK repository(langtools-ce654f4ecfd8). I add the following files in the src/share/classes/com/sun/tools/javac/comp directory

* Delay.java         Main pass to delay the object allocation
* TreeUtil.java      Some neat functions
* Path.java          classes for recording a path of certain statements in the syntax tree
* some other files slightly modified.
