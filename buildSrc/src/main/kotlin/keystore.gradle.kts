// build.gradle.kts

import java.io.File
import org.gradle.api.Project

interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}

interface Keystore {
    /**
     * Генерирует хранилище ключей (keystore) с помощью утилиты keytool.
     * Эта функция является функцией-расширением для Project,
     * поэтому у нее есть доступ к методам Gradle, таким как exec {}.
     */
    fun Project.generateKeystore(keystoreFile: File) {
        objects.newInstance<InjectedExecOps>().execOps.exec {
            executable = "keytool"

            args(
                "-genkey",
                "-keystore", keystoreFile.path,
                "-alias", "appauth",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-keypass", "appauth",
                "-storepass", "appauth",
                "-dname", "CN=appauth"
            )
        }
    }

    /**
     * Проверяет наличие файла appauth.keystore в корневом каталоге проекта.
     * Если файл отсутствует, вызывает функцию generateKeystore для его создания.
     */
    fun Project.verifyKeystore() {
        val keystoreFile = rootProject.file("appauth.keystore")
        if (!keystoreFile.exists()) {
            logger.lifecycle("Файл appauth.keystore отсутствует - создается новый.")
            generateKeystore(keystoreFile)
        }
    }
}

extensions.create<Keystore>("keystore")