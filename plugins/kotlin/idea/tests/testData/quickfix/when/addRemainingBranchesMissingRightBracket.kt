// "Add remaining branches" "false"
// ACTION: Enable 'Types' inlay hints
// WITH_STDLIB
// ERROR: 'when' expression must be exhaustive, add necessary 'is B' branch or 'else' branch instead

sealed class A
class B : A()

fun test(a: A) {
  val i = w<caret>hen (a) {
