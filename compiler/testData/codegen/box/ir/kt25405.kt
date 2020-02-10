// IGNORE_BACKEND: WASM
// IGNORE_BACKEND_FIR: JVM_IR

fun <T> tableView(init: Table<T>.() -> Unit) {
    Table<T>().init()
}

var result = "fail"

class Table<T> {

    inner class TableColumn(val name: String) {

    }

    fun column(name: String, init: TableColumn.() -> Unit) {
        val column = TableColumn(name).init()
    }
}

fun foo() {
    tableView<String> {
        column("OK") {
            result = name
        }
    }
}

fun box(): String {
    foo()
    return result
}
// DONT_TARGET_EXACT_BACKEND: WASM
 //DONT_TARGET_WASM_REASON: UNRESOLVED_REF__ WASM_FUNCTION_REFERENCES_UNSUPPORTED
