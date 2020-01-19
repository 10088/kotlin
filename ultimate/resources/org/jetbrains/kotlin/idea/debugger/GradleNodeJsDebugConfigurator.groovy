import org.gradle.api.Task

({
    def doIfInstance = { Task task, String fqn, Closure action ->
        def taskSuperClass = task.class
        while (taskSuperClass != Object.class) {
            if (taskSuperClass.canonicalName == fqn) {
                action()

                return
            } else {
                taskSuperClass = taskSuperClass.superclass
            }
        }
    }

    def forNodeJsTask = { Task task, Closure action ->
        doIfInstance(task, "org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec", action)
    }

    def forNodeJsTestTask = { Task task, Closure action ->
        if (task.name.toLowerCase().contains("node".toLowerCase())) {
            doIfInstance(task, "org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest", action)
        }
    }

    gradle.taskGraph.beforeTask { Task task ->
        forNodeJsTask(task) {
            if (task.hasProperty('args') && task.args) {
                ForkedDebuggerHelper.setupDebugger('%id', task.path, '', '%dispatchPort'.toInteger())
                task.args = ['--inspect-brk'] + task.args
            }
        }

        forNodeJsTestTask(task) {
            if (task.hasProperty('debug')) {
                ForkedDebuggerHelper.setupDebugger('%id', task.path, '', '%dispatchPort'.toInteger())
                task.debug = true
            }
        }
    }

    gradle.taskGraph.afterTask { Task task ->
        forNodeJsTask(task) {
            ForkedDebuggerHelper.signalizeFinish('%id', task.path, '%dispatchPort'.toInteger())
        }

        forNodeJsTestTask(task) {
            ForkedDebuggerHelper.signalizeFinish('%id', task.path, '%dispatchPort'.toInteger())
        }
    }
})()