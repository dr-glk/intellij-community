class PlatfromDerived : <!HIGHLIGHTING("severity='ERROR'; descr='[SEALED_INHERITOR_IN_DIFFERENT_MODULE] Inheritance of sealed classes or interfaces from different module is prohibited'")!>Base<!>()

fun test_2(b: Base) = when (b) {
    is Derived -> 1
}