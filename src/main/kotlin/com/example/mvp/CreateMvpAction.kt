package com.example.mvpcreator

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import java.awt.BorderLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*

class CreateMvpAction : AnAction("Create MVP Package") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null &&
                    e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val baseDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let {
            PsiManager.getInstance(project).findDirectory(it)
        } ?: return

        // 创建自定义对话框（复选框默认不选中）
        val dialogPanel = JPanel(BorderLayout(5, 5)).apply {
            add(JLabel("Enter package name (e.g. bluetooth):"), BorderLayout.NORTH)
            add(JTextField(30).apply {
                name = "packageInput"
            }, BorderLayout.CENTER)
            add(JCheckBox("Create Base interfaces (BasePresenter/BaseView)", false).apply {
                name = "baseCheckbox"
            }, BorderLayout.SOUTH)
        }

        val result = JOptionPane.showConfirmDialog(
            null,
            dialogPanel,
            "Create MVP Package",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (result != JOptionPane.OK_OPTION) return

        val packageName = (dialogPanel.getComponent(1) as JTextField).text.takeIf { it.isNotBlank() } ?: return
        val createBase = (dialogPanel.getComponent(2) as JCheckBox).isSelected

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating MVP Structure", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        runWriteAction {
                            // 获取基础包名（去掉最后一级）
                            val basePackage = if (packageName.contains('.')) {
                                packageName.substringBeforeLast('.')
                            } else {
                                // 如果只有一级包名，则放在上一级目录的base包中
                                val parentDir = baseDir.parent?.takeIf { it.name != "java" && it.name != "src" }
                                    ?: baseDir
                                createPackageHierarchy(parentDir, "base")
                                ""
                            }

                            val classNamePrefix = packageName.substringAfterLast('.')
                                .replaceFirstChar { it.uppercase() }

                            // 创建Base包和接口（与目标包同级）
                            if (createBase) {
                                val basePackagePath = if (basePackage.isEmpty()) "base" else "$basePackage.base"
                                val basePackageDir = createPackageHierarchy(baseDir, basePackagePath)

                                createJavaFile(
                                    project = project,
                                    dir = basePackageDir,
                                    name = "BasePresenter",
                                    content = """|package ${if (basePackage.isEmpty()) "base" else "$basePackage.base"};
                                                |
                                                |/**
                                                | * Author by ${System.getProperty("user.name")}, 
                                                | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                                | */
                                                |public interface BasePresenter {
                                                |    void start();
                                                |}""".trimMargin()
                                )

                                createJavaFile(
                                    project = project,
                                    dir = basePackageDir,
                                    name = "BaseView",
                                    content = """|package ${if (basePackage.isEmpty()) "base" else "$basePackage.base"};
                                                |
                                                |/**
                                                | * Author by ${System.getProperty("user.name")}, 
                                                | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                                | */
                                                |public interface BaseView<T> {
                                                |    void setPresenter(T presenter);
                                                |}""".trimMargin()
                                )
                            }

                            // 创建主包目录
                            val packageDir = createPackageHierarchy(baseDir, packageName)

                            // 生成Contract（完美格式化）
                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                name = "I${classNamePrefix}Contract",
                                content = """|package $packageName;
                                            |
                                            |${if (createBase)
                                    "import ${if (basePackage.isEmpty()) "base" else "$basePackage.base"}.BasePresenter;\n" +
                                            "import ${if (basePackage.isEmpty()) "base" else "$basePackage.base"}.BaseView;"
                                else ""}
                                            |/**
                                            | * Author by ${System.getProperty("user.name")}, 
                                            | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public interface I${classNamePrefix}Contract {
                                            |    interface View extends ${if (createBase) "BaseView<Presenter>" else "Object"} {
                                            |        // Add your view methods here
                                            |    }
                                            |
                                            |    interface Presenter extends ${if (createBase) "BasePresenter" else "Object"} {
                                            |        void destroy();
                                            |    }
                                            |
                                            |    interface Model {
                                            |        void destroy();
                                            |    }
                                            |}""".trimMargin()
                            )

                            // 生成Activity（完美格式化）
                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                name = "${classNamePrefix}Activity",
                                content = """|package $packageName;
                                            |
                                            |import android.os.Bundle;
                                            |import androidx.appcompat.app.AppCompatActivity;
                                            |${if (createBase)
                                    "import ${if (basePackage.isEmpty()) "base" else "$basePackage.base"}.BaseView;"
                                else ""}
                                            |/**
                                            | * Author by ${System.getProperty("user.name")}, 
                                            | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public class ${classNamePrefix}Activity extends AppCompatActivity 
                                            |    implements I${classNamePrefix}Contract.View {
                                            |    
                                            |    private ${classNamePrefix}Presenter presenter;
                                            |
                                            |    @Override
                                            |    protected void onCreate(Bundle savedInstanceState) {
                                            |        super.onCreate(savedInstanceState);
                                            |        presenter = new ${classNamePrefix}Presenter(this);
                                            |        ${if (createBase) "presenter.start();" else ""}
                                            |    }
                                            |${if (createBase) """
                                            |    @Override
                                            |    public void setPresenter(${classNamePrefix}Presenter presenter) {
                                            |        // Implementation here
                                            |    }
                                            |""" else ""}
                                            |}""".trimMargin()
                            )

                            // 生成Presenter（完美格式化）
                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                name = "${classNamePrefix}Presenter",
                                content = """|package $packageName;
                                            |
                                            |${if (createBase)
                                    "import ${if (basePackage.isEmpty()) "base" else "$basePackage.base"}.BasePresenter;"
                                else ""}
                                            |/**
                                            | * Author by ${System.getProperty("user.name")}, 
                                            | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public class ${classNamePrefix}Presenter 
                                            |    implements I${classNamePrefix}Contract.Presenter${if (createBase) ", BasePresenter" else ""} {
                                            |    
                                            |    private I${classNamePrefix}Contract.View view;
                                            |    private ${classNamePrefix}Model model;
                                            |
                                            |    public ${classNamePrefix}Presenter(I${classNamePrefix}Contract.View view) {
                                            |        this.view = view;
                                            |        this.model = new ${classNamePrefix}Model();
                                            |        ${if (createBase) "this.view.setPresenter(this);" else ""}
                                            |    }
                                            |${if (createBase) """
                                            |    @Override
                                            |    public void start() {
                                            |        // Initialization logic here
                                            |    }
                                            |""" else ""}
                                            |    @Override
                                            |    public void destroy() {
                                            |        model.destroy();
                                            |        view = null;
                                            |    }
                                            |}""".trimMargin()
                            )

                            // 生成Model（完美格式化）
                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                name = "${classNamePrefix}Model",
                                content = """|package $packageName;
                                            |
                                            |/**
                                            | * Author by ${System.getProperty("user.name")}, 
                                            | * Date on ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public class ${classNamePrefix}Model implements I${classNamePrefix}Contract.Model {
                                            |    @Override
                                            |    public void destroy() {
                                            |        // Cleanup resources
                                            |    }
                                            |}""".trimMargin()
                            )
                        }
                    }

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "MVP package created: $packageName${if (createBase) "\nWith Base interfaces" else ""}",
                            "Success"
                        )
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Generation failed: ${ex.message}",
                            "Error"
                        )
                    }
                }
            }
        })
    }

    private fun createPackageHierarchy(baseDir: PsiDirectory, packageName: String): PsiDirectory {
        return packageName.split('.').fold(baseDir) { currentDir, subDir ->
            currentDir.findSubdirectory(subDir) ?: runWriteAction {
                currentDir.createSubdirectory(subDir).also {
                    // 确保目录创建成功
                    if (!it.isValid) throw IllegalStateException("Failed to create directory: $subDir")
                }
            }
        }
    }

    private fun createJavaFile(
        project: Project,
        dir: PsiDirectory,
        name: String,
        content: String
    ) {
        if (dir.findFile("$name.java") != null) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showWarningDialog(
                    project,
                    "File $name.java already exists - skipping",
                    "Warning"
                )
            }
            return
        }

        runWriteAction {
            try {
                val file = dir.createFile("$name.java")
                file.virtualFile.setBinaryContent(content.toByteArray())
            } catch (ex: Exception) {
                throw IllegalStateException("Failed to create file $name.java: ${ex.message}")
            }
        }
    }
}