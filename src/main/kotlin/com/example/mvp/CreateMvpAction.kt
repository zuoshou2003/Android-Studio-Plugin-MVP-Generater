package com.example.mvpcreator

import com.intellij.ide.util.PropertiesComponent
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

class CreateMvpAction : AnAction("创建MVP包结构") {

    companion object {
        private const val LAST_PACKAGE_KEY = "mvp_creator_last_package"
        private const val LAST_BASE_OPTION_KEY = "mvp_creator_last_base_option"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null &&
                    e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let {
            PsiManager.getInstance(project).findDirectory(it)
        } ?: return

        val properties = PropertiesComponent.getInstance(project)
        val lastPackage = properties.getValue(LAST_PACKAGE_KEY)
        val lastBaseOption = properties.getBoolean(LAST_BASE_OPTION_KEY, false)

        val dialogPanel = JPanel(BorderLayout(5, 5)).apply {
            add(JLabel("输入完整包名 (例如: com.project.myapplication.bluetooth):"), BorderLayout.NORTH)
            add(JTextField(lastPackage ?: "", 30).apply {
                name = "packageInput"
            }, BorderLayout.CENTER)
            add(JCheckBox("创建基础接口 (BasePresenter/BaseView)", lastBaseOption).apply {
                name = "baseCheckbox"
            }, BorderLayout.SOUTH)
        }

        val result = JOptionPane.showConfirmDialog(
            null,
            dialogPanel,
            "创建MVP包结构",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (result != JOptionPane.OK_OPTION) return

        val fullPackageName = (dialogPanel.getComponent(1) as JTextField).text.takeIf { it.isNotBlank() } ?: return
        val createBase = (dialogPanel.getComponent(2) as JCheckBox).isSelected

        properties.setValue(LAST_PACKAGE_KEY, fullPackageName)
        properties.setValue(LAST_BASE_OPTION_KEY, createBase)

        var fullPackageForFiles = ""

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成MVP结构", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        runWriteAction {
                            val classNamePrefix = fullPackageName.substringAfterLast('.')
                                .replaceFirstChar { it.uppercase() }

                            val lastPackageName = fullPackageName.substringAfterLast('.')
                            val packageDir = createPackageHierarchy(selectedDir, lastPackageName)

                            val currentPackage = selectedDir.getPackageName()
                            fullPackageForFiles = if (currentPackage.isNotEmpty()) {
                                "$currentPackage.$lastPackageName"
                            } else {
                                lastPackageName
                            }

                            val basePackagePath = if (createBase) {
                                val basePackageDir = createPackageHierarchy(selectedDir, "base")
                                "${selectedDir.getPackageName()}.base"
                            } else {
                                ""
                            }

                            if (createBase && basePackagePath.isNotEmpty()) {
                                val basePackageDir = createPackageHierarchy(selectedDir, "base")

                                createJavaFile(
                                    project = project,
                                    dir = basePackageDir,
                                    packageName = basePackagePath,
                                    name = "BasePresenter",
                                    content = """|package $basePackagePath;
                                                |
                                                |/**
                                                | * 作者: ${System.getProperty("user.name")}, 
                                                | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                                | */
                                                |public interface BasePresenter {
                                                |    void start();
                                                |}""".trimMargin()
                                )

                                createJavaFile(
                                    project = project,
                                    dir = basePackageDir,
                                    packageName = basePackagePath,
                                    name = "BaseView",
                                    content = """|package $basePackagePath;
                                                |
                                                |/**
                                                | * 作者: ${System.getProperty("user.name")}, 
                                                | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                                | */
                                                |public interface BaseView<T> {
                                                |    void setPresenter(T presenter);
                                                |}""".trimMargin()
                                )
                            }

                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                packageName = fullPackageForFiles,
                                name = "I${classNamePrefix}Contract",
                                content = """|package $fullPackageForFiles;
                                            |
                                            |${if (createBase && basePackagePath.isNotEmpty())
                                    "import $basePackagePath.BasePresenter;\n" +
                                            "import $basePackagePath.BaseView;"
                                else ""}
                                            |/**
                                            | * 作者: ${System.getProperty("user.name")}, 
                                            | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public interface I${classNamePrefix}Contract {
                                            |    interface View extends ${if (createBase) "BaseView<Presenter>" else "Object"} {
                                            |        
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

                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                packageName = fullPackageForFiles,
                                name = "${classNamePrefix}Activity",
                                content = """|package $fullPackageForFiles;
                                            |
                                            |import android.os.Bundle;
                                            |import androidx.appcompat.app.AppCompatActivity;
                                            |${if (createBase && basePackagePath.isNotEmpty())
                                    "import $basePackagePath.BaseView;"
                                else ""}
                                            |/**
                                            | * 作者: ${System.getProperty("user.name")}, 
                                            | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public class ${classNamePrefix}Activity extends AppCompatActivity 
                                            |    implements I${classNamePrefix}Contract.View {
                                            |    
                                            |    private I${classNamePrefix}Contract.Presenter mPresenter;
                                            |
                                            |    @Override
                                            |    protected void onCreate(Bundle savedInstanceState) {
                                            |        super.onCreate(savedInstanceState);
                                            |        mPresenter = new ${classNamePrefix}Presenter(this);
                                            |        ${if (createBase) "//presenter.start();" else ""}
                                            |    }
                                            |${if (createBase) """
                                            |    @Override
                                            |    public void setPresenter(I${classNamePrefix}Contract.Presenter presenter) {
                                            |        this.mPresenter = presenter;
                                            |    }
                                            |""" else ""}
                                            |}""".trimMargin()
                            )

                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                packageName = fullPackageForFiles,
                                name = "${classNamePrefix}Presenter",
                                content = """|package $fullPackageForFiles;
                                            |
                                            |${if (createBase && basePackagePath.isNotEmpty())
                                    "import $basePackagePath.BasePresenter;"
                                else ""}
                                            |/**
                                            | * 作者: ${System.getProperty("user.name")}, 
                                            | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
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
                                            |        // 在此初始化逻辑
                                            |    }
                                            |""" else ""}
                                            |    @Override
                                            |    public void destroy() {
                                            |        model.destroy();
                                            |        view = null;
                                            |    }
                                            |}""".trimMargin()
                            )

                            createJavaFile(
                                project = project,
                                dir = packageDir,
                                packageName = fullPackageForFiles,
                                name = "${classNamePrefix}Model",
                                content = """|package $fullPackageForFiles;
                                            |
                                            |/**
                                            | * 作者: ${System.getProperty("user.name")}, 
                                            | * 日期: ${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}.
                                            | */
                                            |public class ${classNamePrefix}Model implements I${classNamePrefix}Contract.Model {
                                            |    @Override
                                            |    public void destroy() {
                                            |        // 清理资源
                                            |    }
                                            |}""".trimMargin()
                            )
                        }
                    }

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "MVP包创建成功: $fullPackageForFiles${if (createBase) "\n包含基础接口" else ""}",
                            "成功"
                        )
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "生成失败: ${ex.message}",
                            "错误"
                        )
                    }
                }
            }
        })
    }

    private fun PsiDirectory.getPackageName(): String {
        val path = this.virtualFile.path
        val javaIndex = path.indexOf("java/")
        if (javaIndex == -1) return ""

        val packagePath = path.substring(javaIndex + 5)
        return packagePath.replace('/', '.')
    }

    private fun createPackageHierarchy(baseDir: PsiDirectory, packageName: String): PsiDirectory {
        return packageName.split('.').fold(baseDir) { currentDir, subDir ->
            currentDir.subdirectories.firstOrNull {
                it.name.equals(subDir, ignoreCase = true)
            } ?: runWriteAction {
                currentDir.createSubdirectory(subDir.lowercase()).also {
                    if (!it.isValid) throw IllegalStateException("创建目录失败: $subDir")
                }
            }
        }
    }

    private fun createJavaFile(
        project: Project,
        dir: PsiDirectory,
        packageName: String,
        name: String,
        content: String
    ) {
        val fileName = "${name}.java"

        if (dir.findFile(fileName) != null) {
            ApplicationManager.getApplication().invokeLater {
                Messages.showWarningDialog(
                    project,
                    "文件 $fileName 已存在 - 跳过创建",
                    "警告"
                )
            }
            return
        }

        runWriteAction {
            try {
                val file = dir.createFile(fileName)
                file.virtualFile.setBinaryContent(content.toByteArray())
            } catch (ex: Exception) {
                throw IllegalStateException("创建文件 $fileName 失败: ${ex.message}")
            }
        }
    }
}